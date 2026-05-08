/**
 * MySqlProtocolReuseRule — Wave C cross-kind reuse test kit.
 *
 * Wave C umbrella §8 mandates that every kind reusing MySQL-protocol code
 * (mysql / mariadb / apache_doris / starrocks / tidb / future oceanbase
 * MySQL-mode) provide a concrete subclass of every Abstract*ReuseTest base
 * in this package. Subclasses provide only the kindUnderTest() identifier
 * and the DataSource fixture; assertions live here.
 *
 * Naming is intentionally neutral. Do not introduce kind-anchored names
 * (e.g. TiDbSplitterTest) inside this package; that would create
 * later-refactor debt when oceanbase reuses the code.
 *
 * See: docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md §8
 *      docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md §10
 */
package com.datatalk.application.coverage.mysqlprotocol;
