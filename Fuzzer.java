import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

         if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

         String seedInput = "<html a=\"value\">...</html>";

         ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
         System.out.printf("Command: %s\n", builder.command());
            runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(Fuzzer::random_sub_string, Fuzzer::random_bit_flip, Fuzzer::remove_random_sub_string, Fuzzer::add_random_chars,
                    Fuzzer::swap_random_substring
            )));
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs){
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {System.out.printf("Input: %s\n", input);
                    try {
                        Process process = builder.start();
                        OutputStream streamToCommand = process.getOutputStream();
                        streamToCommand.write(input.getBytes());
                        streamToCommand.flush();
                        streamToCommand.close();
                        int exitCode = process.waitFor();
                        System.out.printf("Exit code: %s\n", exitCode);

                        InputStream streamFromCommand = process.getInputStream();
                        String output = readStreamIntoString(streamFromCommand);
                        streamFromCommand.close();
                        System.out.printf("Output: %s\n", output
                                .replaceAll("warning: this program uses gets\\(\\), which is unsafe.", "")
                                .trim()
                        );

                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        List<String> result = new ArrayList<>();
        for(Function<String, String> mutator : mutators) {
            for(int i=0;i<200;i++) {
                result.add(mutator.apply(seedInput));
            }
        }
        return result;
    }

    private static String random_sub_string(String seedInput){
        int i = (int )(Math.random() * seedInput.length());
        int j = (int )(Math.random() * seedInput.length());
        while (i == j){j = (int )(Math.random() * seedInput.length());}
        if (i > j){int temp = i;i=j;j=temp;}
        return seedInput.substring(i, j);
    }
    private static String remove_random_sub_string(String seedInput){
        int i = (int )(Math.random() * seedInput.length());
        int j = (int )(Math.random() * seedInput.length());
        while (i == j){j = (int )(Math.random() * seedInput.length());}
        if (i > j){int temp = i;i=j;j=temp;}
        return (seedInput.substring(0, i)+seedInput.substring(j,seedInput.length()));
    }
    private static  String add_random_chars(String seedInput){
        String result =  seedInput;
        for (int i = 0; i < Math.random()*100+1; i++) {
            char char_to_be_added = (char) (Math.random() *127);
            int index = (int)(Math.random()*result.length());
            result = result.substring(0, index) + char_to_be_added + result.substring(index, result.length());
        }
        return result;

    }
    private static String random_bit_flip(String seedInput){
        int index = (int)(Math.random() * seedInput.length());
        return seedInput.substring(0, index) + (char) ((int)seedInput.charAt(index)^1<<(int)(Math.random()*6)) + seedInput.substring(index+1, seedInput.length());
    }
    private static String swap_random_substring(String seedInput){
        SortedSet<Integer> sortedSet = new TreeSet<>();
        while(sortedSet.size() <4){
            sortedSet.add((int)(Math.random()*seedInput.length()));
        }
        Iterator<Integer> iter= sortedSet.iterator();
        int i=iter.next();
        int j=iter.next();
        int k=iter.next();
        int l=iter.next();
        return seedInput.substring(0,i)+seedInput.substring(k,l)+seedInput.substring(j,k)+seedInput.substring(i,j)+seedInput.substring(l,seedInput.length());
    }
}
//java Fuzzer.java html_parser_win_x86_64.exe
