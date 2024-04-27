/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.comet

import java.io.File

import scala.util.Random

import org.apache.spark.sql.{CometTestBase, DataFrame, SaveMode}
import org.apache.spark.sql.execution.adaptive.AdaptiveSparkPlanHelper
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.types.{DataType, DataTypes}

class CometCastSuite extends CometTestBase with AdaptiveSparkPlanHelper {
  import testImplicits._

  private val dataSize = 1000

  // we should eventually add more whitespace chars here as documented in
  // https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#isWhitespace-char-
  // but this is likely a reasonable starting point for now
  private val whitespaceChars = " \t\r\n"

  /**
   * We use these characters to construct strings that potentially represent valid numbers such as
   * `-12.34d` or `4e7`. Invalid numeric strings will also be generated, such as `+e.-d`.
   */
  private val numericPattern = "0123456789deEf+-." + whitespaceChars

  private val datePattern = "0123456789/" + whitespaceChars
  private val timestampPattern = "0123456789/:T" + whitespaceChars

  ignore("cast long to short") {
    castTest(generateLongs, DataTypes.ShortType)
  }

  ignore("cast float to bool") {
    castTest(generateFloats, DataTypes.BooleanType)
  }

  ignore("cast float to int") {
    castTest(generateFloats, DataTypes.IntegerType)
  }

  ignore("cast float to string") {
    castTest(generateFloats, DataTypes.StringType)
  }

  test("cast string to bool") {
    val testValues =
      (Seq("TRUE", "True", "true", "FALSE", "False", "false", "1", "0", "", null) ++
        generateStrings("truefalseTRUEFALSEyesno10" + whitespaceChars, 8)).toDF("a")
    castTest(testValues, DataTypes.BooleanType)
  }

  private val castStringToIntegralInputs: Seq[String] = Seq(
    "",
    ".",
    "+",
    "-",
    "+.",
    "-.",
    "-0",
    "+1",
    "-1",
    ".2",
    "-.2",
    "1e1",
    "1.1d",
    "1.1f",
    Byte.MinValue.toString,
    (Byte.MinValue.toShort - 1).toString,
    Byte.MaxValue.toString,
    (Byte.MaxValue.toShort + 1).toString,
    Short.MinValue.toString,
    (Short.MinValue.toInt - 1).toString,
    Short.MaxValue.toString,
    (Short.MaxValue.toInt + 1).toString,
    Int.MinValue.toString,
    (Int.MinValue.toLong - 1).toString,
    Int.MaxValue.toString,
    (Int.MaxValue.toLong + 1).toString,
    Long.MinValue.toString,
    Long.MaxValue.toString,
    "-9223372036854775809", // Long.MinValue -1
    "9223372036854775808" // Long.MaxValue + 1
  )

  test("cast string to byte") {
    // test with hand-picked values
    castTest(castStringToIntegralInputs.toDF("a"), DataTypes.ByteType)
    // fuzz test
    castTest(generateStrings(numericPattern, 5).toDF("a"), DataTypes.ByteType)
  }

  test("cast string to short") {
    // test with hand-picked values
    castTest(castStringToIntegralInputs.toDF("a"), DataTypes.ShortType)
    // fuzz test
    castTest(generateStrings(numericPattern, 5).toDF("a"), DataTypes.ByteType)
  }

  test("cast string to int") {
    // test with hand-picked values
    castTest(castStringToIntegralInputs.toDF("a"), DataTypes.ByteType)
    // fuzz test
    castTest(generateStrings(numericPattern, 5).toDF("a"), DataTypes.ByteType)
  }

  test("cast string to long") {
    // test with hand-picked values
    castTest(castStringToIntegralInputs.toDF("a"), DataTypes.ByteType)
    // fuzz test
    castTest(generateStrings(numericPattern, 5).toDF("a"), DataTypes.ByteType)
  }

  ignore("cast string to float") {
    castTest(generateStrings(numericPattern, 8).toDF("a"), DataTypes.FloatType)
  }

  ignore("cast string to double") {
    castTest(generateStrings(numericPattern, 8).toDF("a"), DataTypes.DoubleType)
  }

  ignore("cast string to decimal") {
    val values = generateStrings(numericPattern, 8).toDF("a")
    castTest(values, DataTypes.createDecimalType(10, 2))
    castTest(values, DataTypes.createDecimalType(10, 0))
    castTest(values, DataTypes.createDecimalType(10, -2))
  }

  ignore("cast string to date") {
    castTest(generateStrings(datePattern, 8).toDF("a"), DataTypes.DoubleType)
  }

  ignore("cast string to timestamp") {
    val values = Seq("2020-01-01T12:34:56.123456", "T2") ++ generateStrings(timestampPattern, 8)
    castTest(values.toDF("a"), DataTypes.DoubleType)
  }

  private def generateFloats(): DataFrame = {
    val r = new Random(0)
    Range(0, dataSize).map(_ => r.nextFloat()).toDF("a")
  }

  private def generateLongs(): DataFrame = {
    val r = new Random(0)
    Range(0, dataSize).map(_ => r.nextLong()).toDF("a")
  }

  private def generateString(r: Random, chars: String, maxLen: Int): String = {
    val len = r.nextInt(maxLen)
    Range(0, len).map(_ => chars.charAt(r.nextInt(chars.length))).mkString
  }

  private def generateStrings(chars: String, maxLen: Int): Seq[String] = {
    val r = new Random(0)
    Range(0, dataSize).map(_ => generateString(r, chars, maxLen))
  }

  private def castTest(input: DataFrame, toType: DataType): Unit = {
    withTempPath { dir =>
      val data = roundtripParquet(input, dir).coalesce(1)
      data.createOrReplaceTempView("t")

      withSQLConf((SQLConf.ANSI_ENABLED.key, "false")) {
        // cast() should return null for invalid inputs when ansi mode is disabled
        val df = spark.sql(s"select a, cast(a as ${toType.sql}) from t order by a")
        checkSparkAnswer(df)

        // try_cast() should always return null for invalid inputs
        val df2 =
          spark.sql(s"select a, try_cast(a as ${toType.sql}) from t order by a")
        checkSparkAnswer(df2)
      }

      // with ANSI enabled, we should produce the same exception as Spark
      withSQLConf(
        (SQLConf.ANSI_ENABLED.key, "true"),
        (CometConf.COMET_ANSI_MODE_ENABLED.key, "true")) {

        // cast() should throw exception on invalid inputs when ansi mode is enabled
        val df = data.withColumn("converted", col("a").cast(toType))
        checkSparkMaybeThrows(df) match {
          case (None, None) =>
          // neither system threw an exception
          case (None, Some(e)) =>
            // Spark succeeded but Comet failed
            throw e
          case (Some(e), None) =>
            // Spark failed but Comet succeeded
            fail(s"Comet should have failed with ${e.getCause.getMessage}")
          case (Some(sparkException), Some(cometException)) =>
            // both systems threw an exception so we make sure they are the same
            val sparkMessage = sparkException.getCause.getMessage
            // We have to workaround https://github.com/apache/datafusion-comet/issues/293 here by
            // removing the "Execution error: " error message prefix that is added by DataFusion
            val cometMessage = cometException.getCause.getMessage
              .replace("Execution error: ", "")
            if (CometSparkSessionExtensions.isSpark34Plus) {
              assert(cometMessage == sparkMessage)
            } else {
              // Spark 3.2 and 3.3 have a different error message format so we can't do a direct
              // comparison between Spark and Comet.
              // Spark message is in format `invalid input syntax for type TYPE: VALUE`
              // Comet message is in format `The value 'VALUE' of the type FROM_TYPE cannot be cast to TO_TYPE`
              // We just check that the comet message contains the same invalid value as the Spark message
              val sparkInvalidValue = sparkMessage.substring(sparkMessage.indexOf(':') + 2)
              assert(cometMessage.contains(sparkInvalidValue))
            }
        }

        // try_cast() should always return null for invalid inputs
        val df2 =
          spark.sql(s"select a, try_cast(a as ${toType.sql}) from t order by a")
        checkSparkAnswer(df2)
      }
    }
  }

  private def roundtripParquet(df: DataFrame, tempDir: File): DataFrame = {
    val filename = new File(tempDir, s"castTest_${System.currentTimeMillis()}.parquet").toString
    df.write.mode(SaveMode.Overwrite).parquet(filename)
    spark.read.parquet(filename)
  }

}
