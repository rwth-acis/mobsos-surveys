MobSOS Surveys
==============
[![Build Status](https://travis-ci.org/rwth-acis/mobsos-surveys.svg?branch=master)](https://travis-ci.org/rwth-acis/mobsos-surveys)

A simple RESTful service for managing, conducting, and evaluating online surveys, including a template-based responsive Web frontend. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer).

Requirements
--

  * Installed Java 8 JDK 
  * Installed [Apache Ant](https://github.com/rwth-acis/LAS2peer)
  * Installed [mySQL 5](http://www.mysql.com/)
  
Build
--

First, initialize the MobSOS Surveys database:

```
mysql -u <ADMINUSER> -p < etc/sql/schema.sql
```

Then, build MobSOS Surveys:

```
ant all
```

Start
--

To start MobSOS Surveys, use one of the available start scripts:
  
  * `bin/start_network.bat (Win)`
  * `bin/start_network.sh (Unix, Mac)`

After successful start, MobSOS Surveys is available under

http://localhost:8080/mobsos-surveys

How to run using Docker
-------------------

First build the image:
```bash
docker build . -t mobsos-surveys
```

Then you can run the image like this:

```bash
docker run -e MYSQL_USER=myuser -e MYSQL_PASSWORD=mypasswd -p 8080:8080 -p 9011:9011 mobsos-surveys
```

Replace *myuser* and *mypasswd* with the username and password of a MySQL user with access to a database named *mobsos*.
By default the database host is *mysql* and the port is *3306*.
The Web UI will be available via *http://localhost:8080/mobsos-surveys* and the las2peer node is available via port 9011.

In order to customize your setup you can set further environment variables.

### Node Launcher Variables

Set [las2peer node launcher options](https://github.com/rwth-acis/las2peer-Template-Project/wiki/L2pNodeLauncher-Commands#at-start-up) with these variables.
The las2peer port is fixed at *9011*.

| Variable | Default | Description |
|----------|---------|-------------|
| BOOTSTRAP | unset | Set the --bootstrap option to bootrap with existing nodes. The container will wait for any bootstrap node to be available before continuing. |
| SERVICE_PASSPHRASE | processing | Set the second argument in *startService('<service@version>', '<SERVICE_PASSPHRASE>')*. |
| SERVICE_EXTRA_ARGS | unset | Set additional launcher arguments. Example: ```--observer``` to enable monitoring. |

### Service Variables

Variables for service configuration.

| Variable | Default |
|----------|---------|
| MYSQL_USER | *mandatory* |
| MYSQL_PASSWORD | *mandatory* |
| MYSQL_HOST | mysql |
| MYSQL_PORT | 3306 |
| EP_URL | http://localhost:8080/mobsos/surveys/ |
| STATIC_CONTENT_URL | "" |
| OIDC_SCHEMA | openidconnect |
| OIDC_PROVIDER_NAME | Learning Layers |
| OIDC_PROVIDER_LOGO | https://api.learning-layers.eu/logo.png |
| OIDC_PROVIDER_URL | https://api.learning-layers.eu/o/oauth2 |
| OIDC_CLIENT_ID | f405d2a1-d4a3-4b6a-abab-65896c1167dc |
| QUESTIONNAIRE_SCHEMA_PATH | ./doc/xml/mobsos-questionnaire.xsd |

### Web Connector Variables

Set [WebConnector properties](https://github.com/rwth-acis/las2peer-Template-Project/wiki/WebConnector-Configuration) with these variables.
*httpPort* and *httpsPort* are fixed at *8080* and *8443*.

| Variable | Default |
|----------|---------|
| START_HTTP | TRUE |
| START_HTTPS | FALSE |
| SSL_KEYSTORE | "" |
| SSL_KEY_PASSWORD | "" |
| CROSS_ORIGIN_RESOURCE_DOMAIN | * |
| CROSS_ORIGIN_RESOURCE_MAX_AGE | 60 |
| ENABLE_CROSS_ORIGIN_RESOURCE_SHARING | TRUE |
| OIDC_PROVIDERS | https://api.learning-layers.eu/o/oauth2,https://accounts.google.com |

### Other Variables

| Variable | Default | Description |
|----------|---------|-------------|
| DEBUG  | unset | Set to any value to get verbose output in the container entrypoint script. |

### Volumes

The following places should be persisted in volumes in productive scenarios:

| Path | Description |
|------|-------------|
| /src/node-storage | Pastry P2P storage. |
| /src/etc/startup | Service agent key pair and passphrase. |
| /src/log | Log files. |

*Do not forget to persist you database data*

Dependencies
---
The service uses [MobSOS questionnaire elements](https://github.com/rwth-acis/mobsos-questionnaire-elements) for some components

License
--
MobSOS Surveys is freely distributable under the [MIT License](https://github.com/rwth-acis/mobsos-survey/blob/master/LICENSE).
