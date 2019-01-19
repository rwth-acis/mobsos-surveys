MobSOS Surveys
==============

A simple RESTful service for managing, conducting, and evaluating online surveys, including a template-based responsive Web frontend. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer).

Requirements
--

  * Installed Java 7 JDK 
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

  [http://localhost:8080/mobsos/surveys](http://localhost:8080/mobsos/surveys)
  
License
--
MobSOS Surveys is freely distributable under the [MIT License](https://github.com/rwth-acis/mobsos-survey/blob/master/LICENSE).
