## Examples how to run rhosak

```shell script
jbang .\Rhosak.java --help
```
```shell script
jbang .\Rhosak.java login
```
```shell script
jbang .\Rhosak.java kafka
```
```shell script
jbang .\Rhosak.java kafka list
```
```shell script
jbang .\Rhosak.java kafka create --name [name]
```
```shell script
jbang .\Rhosak.java kafka delete --id [id]
```
```shell script
jbang .\Rhosak.java kafka topic create --name [name]
```
```shell script
jbang .\Rhosak.java service-account list
```
```shell script
jbang .\Rhosak.java service-account create --file-format [json]
```
```shell script
jbang .\Rhosak.java service-account delete --id [id]
```
```shell script
jbang .\Rhosak.java service-account reset-credentials
```
```shell script
jbang .\Rhosak.java kafka acl create ----operation
```
```shell script
jbang .\Rhosak.java kafka acl list
```
```shell script
jbang .\Rhosak.java kafka acl delete
```
---
Ex. `rhoas kafka acl grant-access --producer --consumer --service-account srvc-acct-b70cb2e4-392c-4389-848e-ca149760e589 --topic all --group all`

```shell script
jbang .\Rhosak.java kafka acl grant-access --producer --consumer --service-account <client_id> --topic all --group all
```
```
$ rhoas kafka acl grant-access
❌ Invalid or missing option(s):
* no operation specified: must provide at least one of "--producer" or "--consumer" flag
* principal is missing, provide one of "--user", "--service-account" or "--all-accounts" flags
* "--topic" or "--topic-prefix" flag is required for consumer and producer operations
* "--group" or "--group-prefix" flag is required for consumer operation
```
---
