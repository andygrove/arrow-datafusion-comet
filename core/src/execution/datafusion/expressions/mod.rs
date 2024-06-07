// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! Native DataFusion expressions

pub mod bitwise_not;
pub mod cast;
pub mod checkoverflow;
pub mod if_expr;
mod normalize_nan;
pub mod scalar_funcs;
pub use normalize_nan::NormalizeNaNAndZero;
pub mod avg;
pub mod avg_decimal;
pub mod bloom_filter_might_contain;
pub mod correlation;
pub mod covariance;
<<<<<<< HEAD
pub mod regexp;
=======
pub mod negative;
>>>>>>> apache/main
pub mod stats;
pub mod stddev;
pub mod strings;
pub mod subquery;
pub mod sum_decimal;
pub mod temporal;
pub mod unbound;
mod utils;
pub mod variance;
