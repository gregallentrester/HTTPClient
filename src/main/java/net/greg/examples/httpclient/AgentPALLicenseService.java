package net.greg.examples.httpclient;

import java.io.*;

import java.net.URI;

import java.net.http.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import java.time.*;
import java.util.*;

import com.google.gson.Gson;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;


@RequiredArgsConstructor
@Log4j2
@Service
@Configuration
@EnableEncryptableProperties   // Encryption support for property sources- added by DevOps
public class AgentPALLicenseService {
    private final PalXMLToObject palXMLToObject = new PalXMLToObject();
    private final TransformPalList transformPalList = new TransformPalList();


    private final PalAgentLicenseRepostory palAgentLicenseRepostory;

    /*
    ThreadIO takes int ioListSize, Consumer<List<T>> consumer, String serviceLabel, DatabaseType, and timeoutLimit.
    Not sure how they are using it, but its all over their code. Need to do more research.
    This was added by jr dev who implemented persistence. accept(List<PalAgentLicense> list) method just makes
    calls to the JPA repo he created.
     */

    //private final ThreadIO<PalAgentLicense> threadID = new ThreadIO<>(4000, this::accept, "Pal API License Service",
    //                             DatebaseType.MYSQL, 10);

    private final List<EligibleAgent> errorList = new ArrayList<>();

    // Creates a TrustManager based on a cert read from the resource folder
    // This method should be private. Don't know how I (and Sonar) missed that.
    public TrustManager[] getTrustAllCerts() {
        InputStream input = null;

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("CertName")) {
            if (is != null) {
                input = new BufferedInputStream(is);
            }

            var cerFactory = CertificateFactory.getInstance("X.509");
            var optumCer = cerFactory.generateCertificate(input);
            String keyStoreType = KeyStore.getDefaultType();
            var keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null);
            keyStore.setCertificateEntry("cert", optumCer);

            String algorithm = TrustManagerFactory.getDefaultAlgorithm();
            var trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            trustManagerFactory.init(keyStore);

            return trustManagerFactory.getTrustManagers();

        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            log.error(e.getMessage());
        }

        return null;
    }

    // Get OAuth2 Bearer Token
    // This method also should be private. Don't know how I (and Sonar) missed that.
    public String getToken() {
        String oauth2URI = "${PAL OAUTH2 URI}";

        // Login info for token URI had to be in the request, not the JSon body
        String tokenRequest = "?client_id=" + "${PALCLIENTID}"
                + "&client_secret=" + "${PALSecret}"
                + "&grant_type=" + "client_credentails";

        String uri = "oauth2URI" + tokenRequest;

        try {
            HttpResponse<String> oAuth2Response;

            var sc = SSLContext.getInstance("TLS");

            // Add TrustManager holding cert to SLLContext
            sc.init(null, getTrustAllCerts(), new java.security.SecureRandom());

            //Build HttpClient with SSLContext
            HttpClient oAuth2Client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(60))
                    .sslContext(sc)
                    .build();

            // Sending a JSon request with no body
            HttpRequest oAuth2Request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Context-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            oAuth2Response = oAuth2Client.send(oAuth2Request, HttpResponse.BodyHandlers.ofString());
            log.debug("Token Response Status Code:: {}", oAuth2Response.statusCode());

            if(oAuth2Response.statusCode() == 200) {
                var oAuth2Map = new Gson().fromJson(oAuth2Response.body(), Map.class);
                return(oAuth2Map.get("access_token").toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Main entry point
    public List<PalAgentLicense> getAgentPALLicense(List<EligibleAgent> eligibleAgentList, long date) {
        String palURI = "palURI";
        List<String>employeeIDs = new ArrayList<>();
        Map<String, Integer> agentEmployeeIDs = new HashMap<>();

        for(EligibleAgent ea : eligibleAgentList) {
            String employeeId = ea.getPersonalId();

            // EligibleAgents with null personalIds will not be processed.
            if(employeeId != null) {
                employeeIDs.add(employeeId);
                agentEmployeeIDs.put(employeeId, ea.getAgentId());
            }
        }

        String token = getToken();

        var xmlInput = xmlStringGenerator(employeeIDs);

        try {
            HttpResponse<String> palResponse;

            var sc = SSLContext.getInstance("TLS");
            // Add TrustManager holding cert to SLLContext
            sc.init(null, getTrustAllCerts(), new java.security.SecureRandom());

          HttpClient palClient = HttpClient.newBuilder()
                  .connectTimeout(Duration.ofSeconds(60))
                  .sslContext(sc)
                  .build();

          // Create HttpRequest, passing oauth2 token retrieve above in header
          HttpRequest palRequest = HttpRequest.newBuilder()
                  .uri(URI.create(palURI))
                  .header("Context-Type", "application/xml")
                  .header("Authorization", "Bearer " + token)
                  .POST(HttpRequest.BodyPublishers.ofString(xmlInput))
                  .timeout(Duration.ofSeconds(60))
                  .build();

          palResponse = palClient.send(palRequest, HttpResponse.BodyHandlers.ofString());

          int responseStatusCode = palResponse.statusCode();

          log.debug("Pal Response Status Code:: {}", responseStatusCode);

          // Check status code and  validate xml
          if(responseStatusCode == 200 && palResponse.body().startsWith("<nsl:GetCoveredStatesResponse"))  {
              // Remove namespace from XML
              String palXMLResponse = palResponse.body().replaceAll("<\\/?ns1:(.*?)\\>", "").trim();
              var finalXML = palXMLResponse.substring(0, palXMLResponse.lastIndexOf("<StatusFlag>"));

              // Unmarshall XML
              var employeeList = palXMLToObject.unmarshall(finalXML);
              log.debug("EmployeeInfo List size(): {}", employeeList.getEmployeeInfo().size());

              // Transform EmployeeInfo to AgentPalLicense
              return transformPalList.transform(employeeList.getEmployeeInfo(), agentEmployeeIDs, date);
          } else {
              log.debug("Error from Pal request:: {} - {}", responseStatusCode, palResponse.body());
              errorList.addAll(eligibleAgentList);
              return new ArrayList<>();
          }

        } catch (Exception e) {
            log.error(e.getMessage());   // Sonar complains if you e.printStackTrace()
        }

        errorList.addAll(eligibleAgentList);
        return new ArrayList<>();

    }

    /**
     *
     * @param employeeIDs
     * @return XML String used in PAL HttpRequest
     * Constructs XML String for one or more employeeIDs
     */
    private String xmlStringGenerator(List<String> employeeIDs) {

        var xmlInput = new StringBuilder("getCoveredStatesRequest"
                +"<request>"
                + "<!-- this element may appear multiple times -->");
        String suffix = "</requests>"
                +"</getCovered2StatesRequest>";

        // String generator
        for(String empid : employeeIDs) {
            String temp = "<EmployeeID>"+empid+"</EmployeeID>";
        }

        xmlInput.append(suffix);

        return xmlInput.toString();
    }
}
