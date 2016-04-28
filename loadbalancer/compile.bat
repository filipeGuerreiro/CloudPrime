SET JAVA_HOME=C:\Java\jdk1.7.0_79
SET JAVA_ROOT=C:\Java\jdk1.7.0_79
SET JDK_HOME=C:\Java\jdk1.7.0_79
SET JRE_HOME=C:\Java\jdk1.7.0_79\jre
SET PATH=C:\Java\jdk1.7.0_79\bin;%PATH%
SET SDK_HOME=C:\Java\jdk1.7.0_79

SET AWS_HOME=C:\Users\Filipe\Desktop\git_projects\CloudPrime\aws-java-sdk-1.10.69

SET CLASSPATH=%AWS_HOME%\lib\aws-java-sdk-1.10.69.jar;%AWS_HOME%\third-party\lib\*;.;..

javac -cp %CLASSPATH% *.java

java -cp %CLASSPATH% loadbalancer.LoadBalancer