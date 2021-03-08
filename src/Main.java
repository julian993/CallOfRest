import java.util.Objects;

public class Main {

    public static void main(String[] args) {
        BPERHelper.getInstance().initial();
//        BPERHelper.getInstance().printAllTicket();

        BPERHelper.getInstance().writeTicketOnCedacri("S1");

        BPERHelper.getInstance().endgame();
    }


}
