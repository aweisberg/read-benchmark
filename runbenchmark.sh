#!/bin/sh
JAVA=/home/aweisberg/jdk/bin/java
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 2 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 4 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 8 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 12 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 16 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 24 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 32 600 10 8
sudo sh -c "echo 1 > /proc/sys/vm/drop_caches"
$JAVA -jar readbenchmark.jar /tmp 64 64 600 10 8
