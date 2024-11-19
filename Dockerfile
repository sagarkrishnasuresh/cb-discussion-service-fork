FROM openjdk:8
COPY cb-discussion-service-0.0.1-SNAPSHOT.jar /opt/
CMD ["/bin/bash", "-c", "java -XX:+PrintFlagsFinal $JAVA_OPTIONS -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -jar /opt/cb-discussion-service-0.0.1-SNAPSHOT.jar"]
