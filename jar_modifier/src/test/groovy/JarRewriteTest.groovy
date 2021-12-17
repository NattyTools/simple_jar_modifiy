import org.nattytools.jarmodifier.JarClassWriter

String path = "src/test/resources/test.jar";
File file = new File(path);
String absolutePath = file.getAbsolutePath();


InputStream is = new FileInputStream(absolutePath);

JarClassWriter jarClassWriter = new JarClassWriter() {
    @Override
    boolean visitClass(InputStream input, OutputStream output, String jarFileName,String entryName) throws IOException {
        println(jarFileName+"@"+entryName)

        output.write(new byte[]{ 0,3,4})
        return true;
    }
}





FileOutputStream fileOutputStream = new FileOutputStream("test-mod.jar")
jarClassWriter.modifyZip(is,fileOutputStream,"myjar.jar")

fileOutputStream.close();