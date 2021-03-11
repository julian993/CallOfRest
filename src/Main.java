import java.util.Objects;

public class Main {

    public static void main(String[] args) {
        BPERHelper.getInstance().initial();
//        BPERHelper.getInstance().printAllTicket();
        if (args[0].equals("-readbystatusid")) {
            BPERHelper.getInstance().writeTicketOnCedacri(args[1]);
        } else if (args[0].equals("-resolved")) {
            //-resolved -id 91C -comment "messo in risolto" -solution "questa è la soluzione"
            if (args[1].equals("-id"))
                if (args[3].equals("-comment"))
                    if (args[5].equals("-solution"))
                        BPERHelper.getInstance().changeStatus(args[2], "S3", args[4], args[6]);
        }else{
            BPERHelper.getInstance().error_InputCommnad(args[0]);
        }


        BPERHelper.getInstance().endgame();
    }


}

//-readbystatusid S1
//-resolved -id 919C -comment "chiusura del ticket" -solution "questa è la soluzione"

