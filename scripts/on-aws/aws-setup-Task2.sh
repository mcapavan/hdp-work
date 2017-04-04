#!/usr/bin/env bash
echo "******* Setting up the environment for Task 2 – Data Pipelines in Hive *******"
echo "*** Downloading Employees DB ***"
wget https://launchpad.net/test-db/employees-db-1/1.0.6/+download/employees_db-full-1.0.6.tar.bz2
bzip2 -d employees_db-full-1.0.6.tar.bz2
tar xvf employees_db-full-1.0.6.tar

cd employees_db

echo "*** Replace storage_engine with default_storage_engine to fix latest db dump ***"
sed -i 's/storage_engine/default_storage_engine/g' employees.sql
sed -i 's/storage_engine/default_storage_engine/g' test_employees_md5.sql
sed -i 's/storage_engine/default_storage_engine/g' test_employees_sha.sql

echo "*** Delete MySQL employees DB"
mysql --user=root --password=hadoop -e "drop database if exists employees"

echo "*** Import the Employees DB to Hive's MySQL instance ***"
mysql --user=root --password=hadoop -t < employees.sql
mysql --user=root --password=hadoop -t < test_employees_md5.sql
mysql --user=root --password=hadoop -t < test_employees_sha.sql

mysql --user=root --password=hadoop -e "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'hadoop' WITH GRANT OPTION"
mysql --user=root --password=hadoop -e "FLUSH PRIVILEGES"

echo "*** Clean Hive tables if exist ***"
hive <<EOD
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS employees_new;
DROP TABLE IF EXISTS salaries;
DROP TABLE IF EXISTS salaries_new;
EOD

echo "*** Import employees and salaries from MySQL to Hive by using Sqoop ***"
# To create employees table in Hive
sqoop import --connect jdbc:mysql://ip-172-31-26-241.eu-west-2.compute.internal:3306/employees \
    --username root -password hadoop --table employees --fields-terminated-by "|" --hive-import \
    --driver com.mysql.jdbc.Driver -m 1

# To create salaries table in Hive
sqoop import --connect jdbc:mysql://ip-172-31-26-241.eu-west-2.compute.internal:3306/employees \
    --username root --password hadoop --table salaries --fields-terminated-by "|" --hive-import \
    --driver com.mysql.jdbc.Driver

echo "*** Validating the sqoop import ***"
echo "*** MySQL employees record count: "
mysql --user=root --password=hadoop -e "select count(*) from employees.employees"
echo "*** MySQL salaries record count: "
mysql --user=root --password=hadoop -e "select count(*) from employees.salaries"

echo "*** Hive employees record count: "
hive  -e "select count(*) from employees;"
echo "*** Hive salaries record count: "
hive -e "select count(*) from salaries;"

echo "******* Task 2 – Data Pipelines in Hive setup has been completed *******"