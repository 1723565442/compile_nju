public class Helper {
    public static String toDec(String tokenText){
        if (tokenText.startsWith("0x") || tokenText.startsWith("0X")) {
            tokenText = String.valueOf(Integer.parseInt(tokenText.substring(2), 16));
        } else if (tokenText.startsWith("0")) {
            tokenText = String.valueOf(Integer.parseInt(tokenText, 8));
        }
        return tokenText;
    }
    public static void printSpaceDoubly(int a){
        for (int i = 0;i<a;i++){
            System.err.print("  ");
        }
    }

}
