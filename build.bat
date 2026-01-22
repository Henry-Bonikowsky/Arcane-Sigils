@echo off
cd /d "C:\Users\henry\Projects\Arcane Sigils"
set JAVA_HOME=C:\Users\henry\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.1.8-hotspot
"C:\Users\henry\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6\bin\mvn.cmd" clean package -DskipTests
