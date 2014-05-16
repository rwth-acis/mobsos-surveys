
insert into mobsos.questionnaire(owner, organization, logo, name, description) 
values (123456789,"RWTH","http://bla.org","Do you like Questionnaire","A sample questionnaire");

select * from mobsos.questionnaire;

insert into mobsos.survey(owner, organization, logo, name, description, resource, start, end, qid ) 
values (123456789,"RWTH","http://blub.org","Antonio Banderas Survey",
"A simple survey on Antonio","http://antonio.org","2014-05-30 00:00:00","2014-06-30 23:59:59", 1);

insert into mobsos.survey_result(uid,cid,sid,qkey,qval) values 
(666666666,1212121212,1,"A.2.3",2);

insert into mobsos.survey_result(uid,cid,sid,qkey,qval) values 
(666666666,1212121212,1,"A.2.1","Dominik findet ihn Scheisse");

insert into mobsos.survey_result(uid,cid,sid,qkey,qval) values 
(666666666,1212121212,1,"A.1.1",1);

insert into mobsos.survey_result(uid,cid,sid,qkey,qval) values 
(777777777,1212121212,1,"A.1.1",0);

insert into mobsos.survey_result(uid,cid,sid,qkey,qval) values 
(777777777,1212121212,1,"A.2.1","Ich finde das Scheisse");

select distinct(qkey) from mobsos.survey_result;

select uid, cid,
MAX(IF(qkey = 'A.2.1', cast(qval as unsigned), NULL)) AS "A.2.1",
MAX(IF(qkey = 'A.2.2', cast(qval as unsigned), NULL)) AS "A.2.2",
MAX(IF(qkey = 'A.2.3', qval, NULL)) AS "A.2.3"
from mobsos.survey_result where sid = 33 group by uid, cid;