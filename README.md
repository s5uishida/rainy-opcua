# rainy-opcua
rainy-opcua is a bundle that provides rainy with the function to acquire data with OPC-UA.
I releases this in the form of the Eclipse plug-in project. You need Java 8 or higher.

I have confirmed that it works in Raspberry Pi 3B  ([Raspbian Buster Lite OS](https://www.raspberrypi.org/downloads/raspbian/) (2019-07-10)).

## Install Raspbian Buster Lite OS (2019-07-10)
The reason for using this version is that it is the latest as of July 2019.

## Install jdk8 on Raspberry Pi 3B
For example, the installation of OpenJDK 8 is shown below.
```
# apt-get update
# apt-get install openjdk-8-jdk
```

## Install git
If git is not included, please install it.
```
# apt-get install git
```

## Use this with the following bundles
- [SLF4J 1.7.26](https://www.slf4j.org/)
- [JSR 305 3.0.2](https://mvnrepository.com/artifact/org.apache.servicemix.bundles/org.apache.servicemix.bundles.jsr305/3.0.2_1)
- [Guava: Google Core Libraries for Java 26.0](https://repo1.maven.org/maven2/com/google/guava/guava/26.0-jre/guava-26.0-jre.jar)
- [Netty 4.1.38](https://netty.io/index.html) netty-buffer-4.1.38.Final.jar, netty-codec-4.1.38.Final.jar, netty-codec-http-4.1.38.Final.jar, netty-common-4.1.38.Final.jar, netty-handler-4.1.38.Final.jar, netty-resolver-4.1.38.Final.jar, netty-transport-4.1.38.Final.jar
- [JAXB API 2.3.1](https://mvnrepository.com/artifact/javax.xml.bind/jaxb-api/2.3.1)
- [JAXB Runtime 2.3.2](https://mvnrepository.com/artifact/org.glassfish.jaxb/jaxb-runtime/2.3.2)
- [JavaBeans Activation Framework (JAF) 1.2.0](https://mvnrepository.com/artifact/com.sun.activation/javax.activation/1.2.0)
- [strict-machine-osgi 0.1](https://github.com/s5uishida/strict-machine-osgi)
- [netty-channel-fsm-osgi 0.3](https://github.com/s5uishida/netty-channel-fsm-osgi)
- [bsd-parser-core 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/bsd-parser-core/0.3.4)
- [bsd-parser-gson 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/bsd-parser-gson/0.3.4)
- [stack-core 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/stack-core/0.3.4)
- [stack-client 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/stack-client/0.3.4)
- [sdk-core 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/sdk-core/0.3.4)
- [sdk-client 0.3.4](https://mvnrepository.com/artifact/org.eclipse.milo/sdk-client/0.3.4)
- [Gson 2.8.5](https://mvnrepository.com/artifact/com.google.code.gson/gson/2.8.5)
- [Bouncy Castle PKIX, CMS, EAC, TSP, PKCS, OCSP, CMP, and CRMF APIs 1.62](https://www.bouncycastle.org/download/bcpkix-jdk15on-162.jar)
- [Bouncy Castle Provider 1.62](https://www.bouncycastle.org/download/bcprov-jdk15on-162.jar)
- [Jackson 2.9.9](https://github.com/FasterXML/jackson) [annotations](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-annotations/2.9.9), [core](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core/2.9.9), [databind](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.9.9.1)
- [OkHttp 3.14.1](https://mvnrepository.com/artifact/org.apache.servicemix.bundles/org.apache.servicemix.bundles.okhttp/3.14.1_1)
- [Okio 1.15.0](https://mvnrepository.com/artifact/org.apache.servicemix.bundles/org.apache.servicemix.bundles.okio/1.15.0_1)
- [Retrofit 2.5.0](https://mvnrepository.com/artifact/org.apache.servicemix.bundles/org.apache.servicemix.bundles.retrofit/2.5.0_2)
- [moshi-osgi 1.7.0](https://github.com/s5uishida/moshi-osgi)
- [msgpack-core-osgi 0.8.17](https://github.com/s5uishida/msgpack-core-osgi)
- [influxdb-java-osgi 2.15.0](https://github.com/s5uishida/influxdb-java-osgi)
- [Eclipse Paho Client Mqttv3 1.2.1](https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3/1.2.1)
- [rainy 0.1.24](https://github.com/s5uishida/rainy)

I would like to thank the authors of these very useful codes, and all the contributors.

## How to use
See [rainy](https://github.com/s5uishida/rainy) for how to use this in rainy.
