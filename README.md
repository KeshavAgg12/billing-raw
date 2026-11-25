# Shoe Bazaar - raw Java version (no Spring Boot)

Plain Java 21: JDK HttpServer + JDBC (H2 file database) + Gson for JSON.
The web screens are in src/main/resources/static and are served by the same program.

## Run in IntelliJ
1. File > Open > select this folder (billing-raw, the one with pom.xml). Trust the project, wait for Maven to load.
2. Project Structure: SDK 21.
3. Open src/main/java/com/shop/billing/App.java and run main().
4. Console shows: Shoe Bazaar is running at http://localhost:8081
5. Open http://localhost:8081

The database is created in ./data/billingraw (next to pom.xml) the first time.
Port 8081 is also used by the Spring Boot version, so run only one of them at a time.
Change the port, database file and pricing numbers in src/main/resources/config.properties.

## Test
With the app running, in PowerShell:
    powershell -ExecutionPolicy Bypass -File .\test-api.ps1
(run it once on a fresh database, it creates set 5-9)

## Files (package com.shop.billing)
App.java              main(): starts the database and the HTTP server, serves the screens, sends JSON
Api.java              routing table: URL + method -> service call, reads JSON with Gson
ApiException.java     an error with an HTTP status (400, 404, 409) and a message for the screen
Config.java           reads config.properties
Db.java               JDBC helpers: connection, transaction (commit / rollback), query, update, insert
Models.java           records for requests and responses
SetService.java       master set list (5-8, 11-2)
ArticleService.java   articles, overlap check, sets and loose calculation
StockService.java     stock coming in (purchase)
CustomerService.java  customers, payments, balance
InvoiceService.java   billing: partial stock, pricing rules, saves invoice in one transaction
ReportService.java    purchase, sales, profit for a date range
resources/schema.sql  all tables

## Added later
- Billing screen shows the rate per pair while you type the quantity, a rate and amount for every line in the bill, and an estimated total.
  The 32-pair rule is shown too. The server still works out the final rates when you save.
- "Open an old invoice" can search by customer name (part of the name is enough, capital letters do not matter).
  Endpoints added: GET /api/invoices?customer=name and GET /api/pricing
