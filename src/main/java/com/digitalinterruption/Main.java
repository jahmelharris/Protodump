package com.digitalinterruption;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.apache.commons.text.StringEscapeUtils;

import org.apache.commons.cli.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    static StringBuffer protoFile = new StringBuffer();

    public static HashMap<Integer, String>TypeLookup = new HashMap<Integer, String>(){{
        put(1,"double");
        put(2,"float");
        put(3,"int64");
        put(4,"uint64");
        put(5,"int32");
        put(6,"fixed64");
        put(7,"fixed32");
        put(8,"bool");
        put(9,"string");
        put(10,"group");
        put(11,"message");
        put(12,"bytes");
        put(13,"uint32");
        put(14,"enum");
        put(15,"sfixed32");
        put(16,"sfixed64");
        put(17,"sint32");
        put(18,"sint64");

    }};

    public static HashMap<String, String>LabelLookup = new HashMap<String, String>(){{
        put("LABEL_OPTIONAL","optional");
        put("LABEL_REPEATED","repeated");
        put("LABEL_REQUIRED","required");
    }};
    private static boolean mIsSyntax3;


    public static void main(String[] args) {

        String directory = "";
        String type = "";
        Options options = new Options();

        Option pathArg = Option.builder("p").longOpt("path")
                .argName("path")
                .hasArg()
                .required(true)
                .desc("Path to directory or file to parse").build();
        options.addOption(pathArg);

        Option typeArg = Option.builder("t").longOpt("type")
                .argName("type")
                .hasArg()
                .required(true)
                .desc("[smali | raw] Type of file parse proto file from. Currently only smali and raw are supported").build();
        options.addOption(typeArg);


        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
             directory = cmd.getOptionValue("path");
             type = cmd.getOptionValue("type");


        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ant", options);
            return;
        }

        if(type.equals("smali")) {
            System.out.println("Parsing Smali files in "+directory);
            ScanDirectoryForSmaliFiles(directory);
        }
        else if(type.equals("raw")) {
            System.out.println("Parsing raw file in "+directory);
            try {
                List<String> rawFile = Files.readAllLines(Path.of(directory));
                StringBuilder sb = new StringBuilder();
                for (String str : rawFile) {
                    sb.append(str);
                }
                Convert(StringEscapeUtils.unescapeJava(sb.toString()));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void ScanDirectoryForSmaliFiles(String directory) {
        try {
            Matcher m = Pattern.compile("\\n.+.proto.*").matcher("");

            Files.find(Paths.get(directory),
                            Integer.MAX_VALUE,
                            (filePath, fileAttr) -> filePath.toFile().getName().matches(".*.smali"))
                    .forEach(p -> {ScanFileForString(p);});
        } catch (IOException e) {
            throw new RuntimeException(e);

        }
    }

    private static void ScanFileForString(Path file) {
        Matcher m = Pattern.compile("\"(\\\\n.+.proto.*)\"").matcher("");

        try(Stream<String> lines = Files.lines(file)) {
            lines.flatMap(line -> m.reset(line).results().limit(1))
                    .forEach(mr -> {Convert(StringEscapeUtils.unescapeJava(mr.group(1)));});

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    static void ProcessField(DescriptorProtos.FieldDescriptorProto field) {
        String fieldType = null;
        String label = "";
        if(field.hasLabel()) {
            label = LabelLookup.get(field.getLabel().name());
            if (label.equals("optional") && mIsSyntax3) {
                label = "";
            }
        }

        if(field.getTypeName().equals("")) {
            fieldType = TypeLookup.get(field.getType().getNumber());
        }
        else {
            fieldType = field.getTypeName();
        }
        String entry = label+ " "+fieldType + " " + field.getName() + " = "+field.getNumber();

        protoFile.append(entry);
        if(field.hasDefaultValue()) {
            protoFile.append(" [default = "+field.getDefaultValue()+"]");
        }
       protoFile.append(";");
       protoFile.append(System.getProperty("line.separator"));

   }

    static void ProcessMessageType(DescriptorProtos.DescriptorProto messageType) {
        protoFile.append("message "+messageType.getName()+ " { ");
        protoFile.append(System.getProperty("line.separator"));

        for(var nestedType:messageType.getNestedTypeList()){
            ProcessMessageType(nestedType);
        }
        for (var field:messageType.getFieldList()) {
            ProcessField(field);
        }
        for(var enumType:messageType.getEnumTypeList()) {
            ProcessEnum(enumType);
        }
        protoFile.append("}");
        protoFile.append(System.getProperty("line.separator"));

    }

    static void ProcessEnum(DescriptorProtos.EnumDescriptorProto enumType) {
        protoFile.append("enum "+enumType.getName()+ " { ");
        protoFile.append(System.getProperty("line.separator"));
        for (var value:enumType.getValueList()) {
            protoFile.append(value.getName() + " = "+value.getNumber()+";");
            protoFile.append(System.getProperty("line.separator"));
        }
        protoFile.append("}");
        protoFile.append(System.getProperty("line.separator"));
    }

    static void Convert(String str)
    {
        protoFile.setLength(0);
        DescriptorProtos.FileDescriptorProto proto = null;
        byte[] bytes = str.getBytes( Charset.forName("ISO-8859-1"));

        try {
            proto = DescriptorProtos.FileDescriptorProto.parseFrom(bytes);

            Map<Descriptors.FieldDescriptor,Object> allTopFields = proto.getAllFields();

            String fileName = (String)proto.getField(proto.getDescriptorForType().findFieldByName("name"));
            String packageName = (String)proto.getField(proto.getDescriptorForType().findFieldByName("package"));
            String syntaxVersion = (String)proto.getField(proto.getDescriptorForType().findFieldByName("syntax"));

            syntaxVersion = syntaxVersion.equals("") ? "proto2" : syntaxVersion;

            mIsSyntax3 = syntaxVersion.equals("proto3");

            protoFile.append("syntax = \""+ syntaxVersion +""+"\";");
            protoFile.append(System.getProperty("line.separator"));
            if(!packageName.equals("")) {
                protoFile.append("package " + packageName + ";");
                protoFile.append(System.getProperty("line.separator"));
            }
            protoFile.append(System.getProperty("line.separator"));
            List<String> dependencies = (List<String>)proto.getField(proto.getDescriptorForType().findFieldByName("dependency"));
            for (String dependency: dependencies) {
                protoFile.append("import \""+dependency+"\";");
                protoFile.append(System.getProperty("line.separator"));
            }

            if(proto.hasOptions()) {

                DescriptorProtos.FileOptions options = proto.getOptions();
                if (options.hasJavaOuterClassname()) {
                    protoFile.append("option java_outer_classname = \""+ options.getJavaOuterClassname()+"\";");
                    protoFile.append(System.getProperty("line.separator"));

                }
                if (options.hasJavaPackage()) {
                    protoFile.append("option java_package = \""+ options.getJavaPackage()+"\";");
                    protoFile.append(System.getProperty("line.separator"));

                }
                if (options.hasJavaMultipleFiles()) {
                    protoFile.append("option java_multiple_files = "+ options.getJavaMultipleFiles()+";");
                    protoFile.append(System.getProperty("line.separator"));
                }
                protoFile.append(System.getProperty("line.separator"));
            }

            for (var messageType : proto.getMessageTypeList()) {
                ProcessMessageType(messageType);
            }

            System.out.println("Found proto file: "+fileName);
            Path path = Path.of(fileName);
            if(path.getParent()!=null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path,protoFile.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}