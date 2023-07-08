import java.io.*;
import java.util.Scanner;

public class Main
{
    public static void main(String[] args) throws IOException {
        String line;
        File file = new File(args[0]);
        try(FileReader fileReader = new FileReader(file); BufferedReader bufferedReader = new BufferedReader(fileReader);){
            line = bufferedReader.readLine();
            while (line != null){
                System.out.println(line);
                line = bufferedReader.readLine();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        
    }
}


