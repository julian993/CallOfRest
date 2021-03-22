import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.bmc.arsys.api.*;

public class BPERHelper {
    private static BPERHelper bperHelper = null;
    private static String TOKEN = "";
    private static String BPERSERVER = "";
    private static ARServerUser context;//variabile per le operazioni sul server, lettura e scrittura
    private static FileInputStream inputStream;
    private final static Logger logger = Logger.getLogger(BPERHelper.class.getName());//scrittura su MyLog.log

    private BPERHelper() {
    }

    public static synchronized BPERHelper getInstance() {
        if (bperHelper == null) {
            bperHelper = new BPERHelper();
        }
        return bperHelper;
    }


    public boolean initial() {
        Properties properties = System.getProperties();
        try {
            inputStream = new FileInputStream("config.conf");
            properties.load(inputStream);
            properties.setProperty("java.util.logging.config.file", "config.conf");
            LogManager.getLogManager().readConfiguration();
        } catch (IOException e) {
            return false;
        }
        String ARSUser = properties.getProperty("ARSUser");
        String ARSPassword = properties.getProperty("ARSPassword");
        String ARSServer = properties.getProperty("ARSServer");

        BPERSERVER = properties.getProperty("BPERServer");
        String BPERUser = properties.getProperty("BPERUser");
        String BPERPassword = properties.getProperty("BPERPassword");
        context = new ARServerUser(ARSUser, ARSPassword, "", "", ARSServer, Integer.parseInt(properties.getProperty("ARSPort")));
        logger.info("********************************START*******************************************************");
        logger.info("ConnectionServer: " + context.getServer());
        try {
            URL url = new URL(BPERSERVER + "/WSC/Login?username=" + BPERUser + "&password=" + BPERPassword);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null)
                content.append(inputLine);
            System.out.println(content.toString());
            in.close();
            JsonObject jsonObject = JsonParser.parseString(content.toString()).getAsJsonObject();
            TOKEN = jsonObject.get("requestToken").getAsString();
        } catch (IOException e) {
            logger.warning("Errore TOKEN di autenticazione: " + e.getMessage());
            return false;
        }
        logger.info("TOKEN = " + TOKEN);
        return true;
    }

    public void printAllTicket() {
        Result result = this.getAllTickets();
        System.out.println("Letti " + result.total + " ticket");
        for (Ticket t : result.results) {
//            System.out.println("ID: " + t.ID + ", TicketStatusID: " + t.TicketStatusID + ", Date: " + t.Date + ", Att.N." + (t.Attachments != null ? t.Attachments.size() : "0"));
            System.out.println(t.Problem);
        }
    }


    public Result getAllTickets() {
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Tickets?requestToken=" + TOKEN);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            Gson gson = new Gson();
            Result result = gson.fromJson(content.toString(), Result.class);
            return result;
        } catch (IOException e) {
            logger.warning("Errore getAllTickets: " + e.getMessage());
            return null;
        }
    }


    /**
     * Restituisco una lista di ticket (filtrati per status) con i relativi attachment
     * Vado a leggere sul server BPER la lista di ticket con TicketStatusID=status
     * Richiamo la funzione getAllTickets() per avere la lista di tutti i ticket. Purtroppo l'oggetto che mi viene restituito
     * non ha tutti i campi valorizzati. Qui prendo solo gli ID dei ticket
     * Per ogni ticket, filtrato per status, chiamo al funzione getTicketById(t.ID). Mi viene restituito un oggetto con tutti i campi del ticket valorizzati e il numero di atth.
     * Infine devo chiamare la funzione getAttachmentsByID(a.ID) per farmi restituire l'oggetto completo degli attachemnt
     *
     * @param status codice per TicketStatusID: S1=ASSIGNED, S2=IN CHARGE, S3=RESOLVED
     * @return arraylist di tipo Ticket
     */
    private ArrayList<Ticket> getTicketsByStatus(String status) {
        Result tickets;
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Tickets?requestToken=" + TOKEN + "&filter=[{\"property\":\"TicketStatusID\",\"op\":\"eq\",\"value\":\"" + status + "\"}]");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            Gson gson = new Gson();
            tickets = gson.fromJson(content.toString(), Result.class);
        } catch (IOException e) {
            logger.warning("Errore getTicketsByStatus: " + e.getMessage());
            return null;
        }
        if (tickets == null) return null;
        Ticket ticket_complete = null;
        Attachment attch_complete;
        ArrayList<Ticket> tickets_return = new ArrayList<>();
        for (Ticket t : tickets.results) {
            ticket_complete = this.getTicketById(t.ID);//devo usare questa funzione perchè con getAllTickets() non ho tutti i dati
            if (ticket_complete != null) {
                ArrayList<Attachment> attachments_complete = new ArrayList<>();
                for (Attachment a : ticket_complete.Attachments) {//costruisco gli attachments con tutti i dati
                    attch_complete = this.getAttachmentByID(a.ID);//devo usare questa funzione perchè ticket.attachment non ha tutti i campi valorizzati
                    if (attch_complete != null)
                        attachments_complete.add(attch_complete);
                }
                ticket_complete.setAttachments(attachments_complete);//setto gli attachments completi
                //a questo punto la variabile ticket_complete dovrebbe essere completa di tutti i suoi campi, attachments compresi
                tickets_return.add(ticket_complete);
                logger.info("Ticket letto ID=" + ticket_complete.ID);
            }
        }
        logger.info("Tickets letti da BPER: " + tickets_return.size());
        return tickets_return;
    }

    public void writeTicketOnCedacri(String status) {
        logger.info("Lettura ticket con status: " + status);
        ArrayList<Ticket> tickets = this.getTicketsByStatus(status);
        if (tickets == null) return;
        int count = 0;
        for (Ticket t : tickets) {
            //scrittura su REM-OASI-Dispatcher-IN
            if (write_REM_OASI_Dispatcher_IN(t)) {
                count++;
            }
        }
        logger.info("Ticket Scritti: " + count);
    }

    private boolean write_REM_OASI_Dispatcher_IN(Ticket ticket) {
        String entryIdOut = "";
        String form = "REM-OASI-Dispatcher-IN";
        try {
            Entry entry = new Entry();
            entry.put(536870913, new Value(ticket.ID));
            entry.put(536870977, new Value(ticket.RemoteID));
            entry.put(536870929, new Value(ticket.TicketCode));
            entry.put(536870915, new Value(ticket.TicketStatusID));
            entry.put(536870950, new Value(ticket.TicketTypeID));
            entry.put(8, new Value(ticket.Subject));
            entry.put(536870923, new Value(ticket.Problem));
            entry.put(536870958, new Value(ticket.TicketPriorityID));
            entry.put(536870963, new Value(ticket.Solution));
            entry.put(536870933, new Value(ticket.AssignedUserGroupID));
            entry.put(536870916, new Value(ticket.AssignedUserID));
            entry.put(536870960, new Value(this.StringToTimestamp(ticket.Date)));
            if (ticket.TicketStatusID.equals("S3"))
                entry.put(536870962, new Value(this.StringToTimestamp(ticket.ClosureDate)));
            String testo = ticket.Problem;

            String Categoria_Descrizione = testo.substring(testo.lastIndexOf("Categoria_Descrizione") + "Categoria_Descrizione".length() + 2, testo.indexOf("\n", testo.indexOf("Categoria_Descrizione")));
            String Categoria_Hierarchy = testo.substring(testo.lastIndexOf("Categoria_Hierarchy") + "Categoria_Hierarchy".length() + 2, testo.indexOf("\n", testo.indexOf("Categoria_Hierarchy")));
            String Richiedente_Nominativo = testo.substring(testo.lastIndexOf("Richiedente_Nominativo") + "Richiedente_Nominativo".length() + 2, testo.indexOf("\n", testo.indexOf("Richiedente_Nominativo")));
            String Richiedente_FIL_UFF = testo.substring(testo.lastIndexOf("Richiedente_FIL-UFF") + "Richiedente_FIL-UFF".length() + 2, testo.indexOf("\n", testo.indexOf("Richiedente_FIL-UFF")));
            String Richiedente_Indirizzo = testo.substring(testo.lastIndexOf("Richiedente_Indirizzo") + "Richiedente_Indirizzo".length() + 2, testo.indexOf("\n", testo.indexOf("Richiedente_Indirizzo")));
            String Richiedente_Telefono = testo.substring(testo.lastIndexOf("Richiedente_Telefono") + "Richiedente_Telefono".length() + 2, testo.indexOf("\n", testo.indexOf("Richiedente_Telefono")));
            String Richiedente_Mail = testo.substring(testo.lastIndexOf("Richiedente_Mail") + "Richiedente_Mail".length() + 2, testo.indexOf("\n", testo.indexOf("Richiedente_Mail")));
            String Richiedente_Citta = testo.substring(testo.lastIndexOf("Richiedente_Citta") + "Richiedente_Citta".length() + 2);
            entry.put(536870941, new Value(Categoria_Descrizione));
            entry.put(536870957, new Value(Categoria_Hierarchy));
            entry.put(536870947, new Value(Richiedente_Nominativo));
            entry.put(536870964, new Value(Richiedente_FIL_UFF));
            entry.put(536870930, new Value(Richiedente_Indirizzo));
            entry.put(536870942, new Value(Richiedente_Telefono));
            entry.put(536870943, new Value(Richiedente_Mail));
            entry.put(536870988, new Value(Richiedente_Citta));
            entry.put(536870980, new Value("CREATE"));//campo azione che fa partire l'esito

            entryIdOut = context.createEntry(form, entry);//NON TOGLIERE QUESTO
            logger.info("Ticket " + ticket.ID + " scritto su REM-OASI-Dispatcher-IN");
            //carico gli allegati se ci sono
            write_REM_OASI_Attachment_IN(ticket);
            //se il ticket è in assigned allora lo metto in S2 (in charge)
            if (ticket.TicketStatusID.equals("S1")) this.changeStatus(ticket.ID, "S2", "Ticket preso in carico", "");
        } catch (Exception e) {
            logger.warning("Ticket= " + ticket.ID + ": write_REM_OASI_Dispatcher_IN " + e.getMessage() + ": entryIdOut=" + entryIdOut);
            return false;
        }
        return true;
    }

    private boolean write_REM_OASI_Attachment_IN(Ticket ticket) {
        ArrayList<Attachment> attachments = ticket.Attachments;
        if (attachments == null) return true;
        if (attachments.size() < 1) return true;
        String entryIdOut = "";
        String form = "REM-OASI-Attachment-IN";
        int count_attch = 0;
        try {
            Entry entry = new Entry();
            entry.put(536870919, new Value(ticket.ID));//id ticket
            for (Attachment a : attachments) {//carico tutti gli allegati
                entry.put(536870977, new Value(a.ID));//id attachment
                AttachmentValue attachmentValue = new AttachmentValue(a.FileName, Base64.getDecoder().decode(a.Data));
                entry.put(536880912, new Value(attachmentValue));

                entryIdOut = context.createEntry(form, entry);//NON TOGLIERE QUESTO
                count_attch++;
            }
            logger.info("Ticket " + ticket.ID + " --> " + count_attch + " attachements caricati");
        } catch (Exception e) {
            logger.warning("Ticket " + ticket.ID + ": write_REM_OASI_Attachment_IN " + e.getMessage() + ": entryIdOut=" + entryIdOut);
        }
        return true;
    }


    public Ticket getTicketById(String id) {
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Tickets/" + id + "?requestToken=" + TOKEN);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = br.readLine()) != null) {
                content.append(inputLine);
            }
            br.close();
            Gson gson = new Gson();
            Ticket result = gson.fromJson(content.toString(), Ticket.class);
            return result;
        } catch (IOException e) {
            logger.warning("Errore getTicketById: " + e.getMessage());
            return null;
        }
    }

    public void changeStatus(String ticketID, String TicketStatusID, String Comment, String Solution) {
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Tickets/" + ticketID + "/ChangeStatus?requestToken=" + TOKEN);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = "{\"TicketStatusID\": \"" + TicketStatusID + "\",\"Comment\":\"" + Comment + "\"," +
                    "\"Solution\": \"" + Solution + "\"}";

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = "";
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
                logger.info("Ticket " + ticketID + " cambio stato in " + TicketStatusID + ", response code: " + con.getResponseMessage() + response.toString());
            }

        } catch (IOException e) {
            logger.warning("Errore changeStatus: " + e.getMessage());
        }
    }

    public Attachment getAttachmentByID(String id) {
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Attachments/" + id + "?requestToken=" + TOKEN);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            Gson gson = new Gson();
            Attachment result = gson.fromJson(content.toString(), Attachment.class);
            return result;
        } catch (IOException e) {
            logger.warning("Errore getAttachmentsByID: " + e.getMessage());
            return null;
        }
    }

    public long StringToTimestamp(String s) {
        if (s == null)
            return 0;
        else {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            try {
                cal.setTime(sdf.parse(s));
            } catch (ParseException e) {
                logger.warning("StringToTimestamp: " + s);
            }

            return cal.getTimeInMillis() / 1000;
        }
    }


    class Result {
        ArrayList<Ticket> results;
        int total;
        ArrayList<Link> _links;
    }

    class Ticket {
        ArrayList<Attachment> Attachments;
        String ID;
        String RemoteID;
        String TicketCode;
        String TicketStatusID;
        String TicketTypeID;
        String TicketPriorityID;
        String Subject;
        String Problem;
        String Solution;
        String AssignedUserGroupID;
        String AssignedUserID;
        String Date;
        String ClosureDate;
        ArrayList<Link> _links;

        public void setAttachments(ArrayList<Attachment> attachments) {
            Attachments = attachments;
        }
    }

    class Link {
        String method;
        String rel;
        String href;
    }

    class Attachment {
        String ID;
        String RemoteID;
        String FileName;
        String ContentType;
        String Description;
        String Data;
        ArrayList _links;
    }

    public void error_InputCommnad(String s) {
        logger.warning("Errori nei comandi in input: " + s);
    }

    public void endgame() {
        logger.info("END");
        context.logout();
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
