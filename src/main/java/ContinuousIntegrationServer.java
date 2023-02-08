import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import com.google.gson.Gson;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 Skeleton of a ContinuousIntegrationServer which acts as webhook
 See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler
{
    private Notifications notif;
    // Takes a JSON string as an input and converts it to a JSON object.
    // Necessary properties/attributes are retrieved and stored in a hashmap
    public HashMap<String, String> handleJSONObject(JsonObject jsonObject) throws Exception {
        HashMap<String, String> hm = new HashMap<>();
        // owner repo SHA commitId?
        try{
            //Retrieves the path of the branch
            String branchName = jsonObject.get("ref").toString().substring(12).replaceAll("\"","");
            hm.put("BranchName", branchName);
        } catch (Exception e) {
            throw new Exception("Something wrong with ref, error: " + e);
        }
        try{
            //Retrieves the name of the repo and its owner
            JsonObject js = jsonObject.getAsJsonObject("repository");
            hm.put("Repo", js.get("name").toString().replaceAll("\"",""));
            hm.put("Owner", js.getAsJsonObject("owner").get("name").toString().replaceAll("\"",""));
        } catch (Exception e) {
            throw new Exception("Something wrong with Repo/owner name, error: " + e);
        }
        try{
            //Retrieves the SHA number i.e head commit Id
            hm.put("SHA", jsonObject.get("after").toString().replaceAll("\"",""));

        } catch (Exception e) {
            throw new Exception("Something wrong with SHA, error: " + e);
        }
        try{
            //Retrieves the clone url
            hm.put("CloneUrl", String.valueOf(jsonObject.getAsJsonObject("repository").get("clone_url")));
        } catch (Exception e){
            throw new Exception("Something wrong with github url, error: " + e);
        }
        try {
            //Retrieve commits information
            JsonObject commitsArray = jsonObject.getAsJsonObject("head_commit");
            hm.put("Message", String.valueOf(commitsArray.get("message")));
            hm.put("Head_Id", String.valueOf(commitsArray.get("id")));
            hm.put("Timestamp", String.valueOf(commitsArray.get("timestamp")));
            hm.put("Author", String.valueOf(commitsArray.getAsJsonObject("committer").get("name")));
        } catch (Exception e){
            throw new Exception("Something wrong with commits info, error: " + e);
        }

        return hm;
    }

    // Checks if the directory for the cloned repository exists, if it does it removes it
    // otherwise it clones the repository to the directory.
    public void cloneRepo(String cloneUrl, String branch) throws IOException, InterruptedException {
        String tempDir = " ./clonedRepo"; //This path can be changed
        System.out.println("Repo cloned to following directory: " + tempDir);
        System.out.println("The clone URL: " + cloneUrl);
        System.out.println("Cloning repository... ");
        String command = "git clone -b "+ branch+ " " + cloneUrl + tempDir;
        String newCommand = command.replaceAll("\"", "");
        Path cloneDir = Paths.get("clonedRepo");
        System.out.println("Path to cloneDir: " + cloneDir);
        System.out.println("File exists: " + Files.exists(cloneDir));
        if(Files.exists(cloneDir)){
            Process process = Runtime.getRuntime().exec("rm -r clonedRepo");
            process.waitFor();
        }
        Process process = Runtime.getRuntime().exec(newCommand);
        process.waitFor();
        if(process.exitValue() != 0){
            System.out.println("Something went wrong with cloning the repo!");
        }else {
            System.out.println("Successfully cloned repository!");
        }
    }

    // Executes maven commands for installing and compiling the cloned repository
    // Flags are used to check if the commands was successfull.
    public HashMap<String,String> installAndCompileRepo(HashMap<String,String> map) throws IOException {
        //Install
        Process process = Runtime.getRuntime().exec("mvn install -f " + "./clonedRepo");
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Maven install: " + process.exitValue());

        //Compile
        Process compileProcess = Runtime.getRuntime().exec("mvn compile -f " + "./clonedRepo");
        try {
            compileProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
        StringBuilder outputFromCommand = new StringBuilder();
        String line = "";
        boolean compileFlag = false;
        while ((line = reader.readLine()) != null) {
            if(line.contains("SUCCESS")){
                compileFlag = true;
                map.put("Status","Success");
                System.out.println("status success");
                break;
            }
            outputFromCommand.append(line);
        }
           if (!compileFlag) {
               map.put("Status", "Error");
               System.out.println("status error");
           }

        System.out.println("Maven compile: " + outputFromCommand);
        System.out.println("Compile status: " + compileFlag);
        return map;
    }

    public void sendNotificationMail(HashMap<String,String> jsonInfo){
         String username = "group8dd2480@gmail.com";
         String password = "group8group8";

        Properties prop = new Properties();
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true"); //TLS

        Session session = Session.getInstance(prop,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            String committer = jsonInfo.get("Author");
            String commitId = jsonInfo.get("Head_Id");
            String branch = jsonInfo.get("BranchName");
            String timestamp = jsonInfo.get("Timestamp");
            String status = jsonInfo.get("Status");

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("group8dd2480@gmail.com"));
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse("rabihanna007@gmail.com, miltonlindblad@gmail.com")
            );
            message.setSubject("Push Status");

                message.setText("Committer : " + committer + "\n" + "CommitId : " + commitId + "\n" + "Timestamp : " +timestamp + "\n" +"Branch : " + branch + "Status : "+ status);



            Transport.send(message);

            System.out.println("Done");

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }


    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException, ServletException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        // here you do all the continuous integration tasks
        // for example
        // 1st clone your repository
        // 2nd compile the code

        String reqString = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        HashMap<String, String> extractedInfo;
        notif = new Notifications();
        if(!reqString.isEmpty()) {
            try {
                JsonObject jsonObject = new Gson().fromJson(reqString, JsonObject.class);
                extractedInfo = handleJSONObject(jsonObject);
                String clone_url = extractedInfo.get("CloneUrl");
                cloneRepo(clone_url,extractedInfo.get("BranchName"));
                extractedInfo = installAndCompileRepo(extractedInfo);
                /*notif.post_status(extractedInfo.get("Owner"),
                        extractedInfo.get("Repo"),
                        extractedInfo.get("SHA"),
                        extractedInfo.get("Status"),
                        "Placeholder Description", "", "");*/
                sendNotificationMail(extractedInfo);
            } catch (Exception e) {
                throw new RuntimeException("Error when calling handleJSON, error: " + e);
            }

            System.out.println("JSON parsed: " + extractedInfo);
        }

        response.getWriter().println("CI job done");
    }



    // used to start the CI server in command line
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer());
        server.start();
        server.join();
    }
}