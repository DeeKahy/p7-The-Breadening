//This file was generated from (Commercial) UPPAAL 4.1.1 (rev. 4155), December 2008

/*
Is this system deadlock free?
*/
A[] not deadlock

/*
Query for TestCase: is it always the case that when user is in "LevelNine" the level is 9
*/
A[] user.LevelNine imply envLevel==9

/*
Query for TestCase: is it always the case that when user is in "LevelTen" the level is 10
*/
A[] user.LevelTen imply envLevel==10
