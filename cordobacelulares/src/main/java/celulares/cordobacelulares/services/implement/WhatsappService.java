package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.whatsapp.MessageBodyDto;
import celulares.cordobacelulares.dtos.whatsapp.RequestMessageText;
import celulares.cordobacelulares.dtos.whatsapp.RequestWhatsapp;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WhatsappService {
    private final RestClient restClient;
    private final String urlBase = "https://graph.facebook.com/v19.0/362573800262598/messages";

    public WhatsappService() {
        restClient = RestClient.builder()
                .baseUrl(urlBase)
                .defaultHeader("Authorization", "Bearer EAAQa0mfqRJ0BOZBcLVjQZBnlLF6O0Da4UtEdQFZArwp2dA7ytZChh9uA1E1B2YcaZB3e1ZB7G38pZBt2bMrx6SPag41weFi6jjP7aZAaLscX7h6JREaSZAMBZCyJpZBQTg6JvrYjiUODtSnRLZAPkhk9GfganVG17fjwmNblLIgiKeBQ3Cl5sDrlZA758eAp4eSQ99ZBYrueVeVAfCRyxbQieaZArSPZCKfKfcuAWL2M")
                .build();
    }

    public void sendMessage(MessageBodyDto messageBodyDto) {
        RequestWhatsapp request = new RequestWhatsapp("whatsapp", messageBodyDto.number(), new RequestMessageText(messageBodyDto.message()));
        try {
            String result = restClient.post().uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex){

            throw new RestClientResponseException("Error al enviar mensaje", 400, "Error",null,null,null);

        }
    }
}
