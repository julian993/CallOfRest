import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class BPERHelper {
    private static BPERHelper bperHelper = null;
    private static String TOKEN = "";

    private BPERHelper() {
    }


    public static synchronized BPERHelper getInstance() {
        if (bperHelper == null) {
            bperHelper = new BPERHelper();
            TOKEN = getRequestTocket("", "");
        }
        return bperHelper;
    }


    private static String getRequestTocket(String user, String psw) {
        URL url = null;
        String requestToken = "";
        try {
            url = new URL("https://bper-stage.customercare.it/HDAPortal/WSC/Login?username=oasicedacri&password=Alten012021!");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            JsonObject jsonObject = JsonParser.parseString(content.toString()).getAsJsonObject();
            requestToken = jsonObject.get("requestToken").getAsString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("TOKEN = " + requestToken);
        return requestToken;
    }

    public Result getAllTickets() {
        try {
            URL url = new URL("https://bper-stage.customercare.it/HDAPortal/API/CC/Tickets?requestToken=" + TOKEN);
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
            System.out.println("Errore getAllTickets: " + e.getMessage());
            return null;
        }
    }

    public void printAllTicket() {
        Result result = this.getAllTickets();
        System.out.println("Letti " + result.total + " ticket");
        for (Ticket t : result.results) {
            System.out.println("ID: "+ t.ID + ", TicketStatusID: " + t.TicketStatusID + ", Date: " + t.Date + ", Att.N." +
                    (t.Attachments != null ? t.Attachments.size() : "0"));
        }
    }

    public Ticket getTicketById(String id){
        try {
            URL url = new URL("https://bper-stage.customercare.it/HDAPortal/API/CC/Tickets/" + id + "?requestToken=" + TOKEN);
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
            System.out.println("Errore getTicketById: " + e.getMessage());
            return null;
        }
    }

    public void changeStatus() {
        try {
            URL url = new URL("https://bper-stage.customercare.it/HDAPortal/API/CC/Tickets/921C/ChangeStatus?requestToken=" + TOKEN);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = "{\"TicketStatusID\": \"" + "S1" + "\",\"Comment\":\"Cambio stato da Java\",\"Solution\": \"Questa è una soluzione\"}";
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
                System.out.println(response.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public Attachment getAttachmentsByID(String id) {
        try {
            URL url = new URL("https://bper-stage.customercare.it/HDAPortal/API/CC/Attachments/" + id + " ?requestToken=" + TOKEN);
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
            System.out.println("Errore getAttachmentsByID: " + e.getMessage());
            return null;
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


}
