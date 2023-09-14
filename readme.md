Write, save, read - all in file editor.

On save the file is processed and feedback is added directly to the file itself.

# Build
Java 17, maven
```
mvn clean package
```

# Build native executable
GraalVM
```
mvn clean package
sdk install java 22.3.3.r17-nik
sdk use java 22.3.3.r17-nik
native-image -Duser.country=PL -Duser.language=pl -jar target/alok-1.0-SNAPSHOT-jar-with-dependencies.jar -o target/alok
```

# Install
Install as a user service (native executable)
```
cd ansible
ansible-playbook install.yml
```

# Tests
Test cases are in `/cases` directory.
Results overwrite the input file, so you can easily diff and accept new results. 
