Write, save, read - all in file editor.

On save the file is processed and feedback is added directly to the file itself.

# Build
Java 11, maven
```
mvn clean package
```

# Cliet certificate
In order to use client certificate provide path and key:
```
-Djavax.net.ssl.keyStore=/home/rgl/cert.p12 
-Djavax.net.ssl.keyStorePassword=****
```

# Tests
Test cases are in `/cases` directory. Results overwrite the input file, so you can easily diff and accept new results. 