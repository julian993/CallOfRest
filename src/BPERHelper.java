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
            System.out.println("ID: " + t.ID + ", TicketStatusID: " + t.TicketStatusID + ", Date: " + t.Date + ", Att.N." +
                    (t.Attachments != null ? t.Attachments.size() : "0"));
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
        ArrayList<Ticket> tickets = this.getTicketsByStatus(status);
        if (tickets == null) return;
        //scrittura su REM-OASI-Dispatcher-IN
        write_REM_OASI_Dispatcher_IN(tickets);


        //scrittura su REM-OASI-Attachment-IN
        write_REM_OASI_Attachment_IN(tickets);

    }

    private boolean write_REM_OASI_Dispatcher_IN(ArrayList<Ticket> tickets) {
        String entryIdOut = "";
        String form = "REM-OASI-Dispatcher-IN";
        int count = 0;
        for (Ticket t : tickets) {
            try {
                Entry entry = new Entry();
                entry.put(536870913, new Value(t.ID));
                entry.put(536870977, new Value(t.RemoteID));
                entry.put(536870929, new Value(t.TicketCode));
                entry.put(536870915, new Value(t.TicketStatusID));
                entry.put(536870950, new Value(t.TicketTypeID));
                entry.put(8, new Value(t.Subject));
                entry.put(536870923, new Value(t.Problem));
                entry.put(536870958, new Value(t.TicketPriorityID));
                entry.put(536870963, new Value(t.Solution));
                entry.put(536870933, new Value(t.AssignedUserGroupID));
                entry.put(536870916, new Value(t.AssignedUserID));
                entry.put(536870960, new Value(this.StringToTimestamp(t.Date)));
                if (t.TicketStatusID.equals("S3"))
                    entry.put(536870962, new Value(this.StringToTimestamp(t.ClosureDate)));
                entryIdOut = context.createEntry(form, entry);//NON TOGLIERE QUESTO
                logger.info("Ticket scritto su REM-OASI-Dispatcher-IN = " + t.ID);
                count++;
            } catch (ARException e) {
                logger.warning("Ticket " + t.ID + ": write_REM_OASI_Dispatcher_IN " + e.getMessage() + ": entryIdOut=" + entryIdOut);
            }
        }
        logger.info("Ticket Scritti: " + count);
        return true;
    }

    private boolean write_REM_OASI_Attachment_IN(ArrayList<Ticket> tickets) {
        String entryIdOut = "";
        String form = "REM-OASI-Attachment-IN";
        for (Ticket t : tickets) {//scorro i ticket
            int count_attch = 0;
            try {
                Entry entry = new Entry();
                entry.put(536870919, new Value(t.ID));//id ticket
                ArrayList<Attachment> attachments = t.Attachments;
                for (Attachment a : attachments) {//carico tutti gli allegati
                    entry.put(536870977, new Value(a.ID));//id attachment
                    AttachmentValue attachmentValue = new AttachmentValue(a.FileName, Base64.getDecoder().decode(a.Data));
                    entry.put(536880912, new Value(attachmentValue));
                    entryIdOut = context.createEntry(form, entry);//NON TOGLIERE QUESTO
                    count_attch++;
                }
                logger.info("Ticket " + t.ID + " --> "+ count_attch + " attachements caricati");
            } catch (ARException e) {
                logger.warning("Ticket " + t.ID + ": write_REM_OASI_Attachment_IN " + e.getMessage() + ": entryIdOut=" + entryIdOut);
            }
        }
        return true;
    }

    public void getAttachments_from_REM_OASI_Attachment_IN() {
        String schemaName = "REM-OASI-Attachment-IN";
        List<EntryListInfo> eListInfos = null;
        String query = "'TicketID BPER'=\"921C\"";
        try {
            QualifierInfo qual = context.parseQualification(schemaName, query);
            eListInfos = context.getListEntry(schemaName, qual, 0, 4000, null, null, false, null);
        } catch (ARException e) {
            logger.warning("getAttachments_from_REM_OASI_Attachment_IN: " + e.getMessage());
            return;
        }
        AttachmentValue attachmentValue;
        for (EntryListInfo eListInfo : eListInfos) {
            Entry record;
            try {
                record = context.getEntry(schemaName, eListInfo.getEntryID(), null);
                attachmentValue = (AttachmentValue) record.get(536880912).getValue();
                System.out.println("Disp_ID: " + attachmentValue.toString());


            } catch (ARException e) {
                logger.warning("getAttachments_from_REM_OASI_Attachment_IN: " + e.getMessage());
            }

        }


    }

    public Ticket getTicketById(String id) {
        try {
            URL url = new URL(BPERSERVER + "/API/CC/Tickets/" + id + "?requestToken=" + TOKEN);
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
            con.setRequestMethod("POST");
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


            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();
                logger.info("changeStatus response: " + response.toString());
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
        private ArrayList<Ticket> results;
        private int total;
        private ArrayList<Link> _links;
    }

    class Ticket {
        private ArrayList<Attachment> Attachments;
        private String ID;
        private String RemoteID;
        private String TicketCode;
        private String TicketStatusID;
        private String TicketTypeID;
        private String TicketPriorityID;
        private String Subject;
        private String Problem;
        private String Solution;
        private String AssignedUserGroupID;
        private String AssignedUserID;
        private String Date;
        private String ClosureDate;
        private ArrayList<Link> _links;

        public void setAttachments(ArrayList<Attachment> attachments) {
            Attachments = attachments;
        }
    }

    class Link {
        private String method;
        private String rel;
        private String href;
    }

    class Attachment {
        private String ID;
        private String RemoteID;
        private String FileName;
        private String ContentType;
        private String Description;
        private String Data;
        private ArrayList _links;

    }

    public void endgame() {
        context.logout();
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
