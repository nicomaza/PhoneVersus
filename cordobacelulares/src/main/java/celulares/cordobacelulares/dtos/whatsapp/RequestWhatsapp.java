package celulares.cordobacelulares.dtos.whatsapp;

import celulares.cordobacelulares.dtos.whatsapp.RequestMessageText;

public record RequestWhatsapp(String messaging_product,
                              String to, RequestMessageText text) {

}
