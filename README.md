# Protodump

Protdump is a tool to extra protobuf .proto definitions from binaries. Currently two modes are supported but more may be added in the future.

## Smali
First convert an APK into smali using apktool (or similar). e.g: 

apktool d app.apk

This will create a directory named "app". Protodump can then be used to scan and extract protobuf files from the descriptors in the smali.

java -jar protodump.jar -p <path/do/decompiled/apk> -t smali

## Raw 
Raw mode is useful if you you have a non supported description you've extracted manually e.g. from a .net or java file. Note: currently only text is supported so probably not useful for native binaries (unless you have the source code).

For example, Person.proto can be extracted from the following descriptor:

```\n\014Person.proto\022\010tutorial\"\333\001\n\006Person\022\014\n\004name\030\001 \001(\t\022\n\n\002id\030\002 \001(\005\022\r\n\005email\030\003 \001(\t\022,\n\006phones\030\004 \003(\0132\034.tutorial.Person.PhoneNumber\032M\n\013PhoneNumber\022\016\n\006number\030\001 \001(\t\022.\n\004type\030\002 \001(\0162\032.tutorial.Person.PhoneType:\004HOME\"+\n\tPhoneType\022\n\n\006MOBILE\020\000\022\010\n\004HOME\020\001\022\010\n\004WORK\020\002\"/\n\013AddressBook\022 \n\006people\030\001 \003(\0132\020.tutorial.PersonB2\n\033com.example.tutorial.protosB\021AddressBookProtosP\001```

java -jar protodump.jar -p </path/to/file/with/extracted/descriptor> -t raw


