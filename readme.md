Write, save, read - all in file editor.

On save the file is processed and feedback is added directly to the file itself.

# Build
Java 11, maven
```
mvn clean package
```

# Tests
Test cases are in `/cases` directory. Results overwrite the input file, so you can easily diff and accept new results. 