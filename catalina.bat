set "CATALINA_BASE=C:\GjergjKadriu\Documents\GitHub\apache-tomcat-10.1.61"
set "JAVA_OPTS=-Xms2g -Xmx4g -XX:MaxDirectMemorySize=2g --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED -Dsakai.home=C:\GjergjKadriu\Documents\GitHub\apache-tomcat-10.1.61\sakai\"

cd "C:\GjergjKadriu\Documents\GitHub\apache-tomcat-10.1.61\bin"
catalina.bat run
