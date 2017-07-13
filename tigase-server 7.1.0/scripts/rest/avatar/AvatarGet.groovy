package tigase.rest.avatar
/*
 * Tigase HTTP API
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
import tigase.http.rest.Service
import tigase.server.Iq
import tigase.util.Base64
import tigase.xml.Element
import tigase.xmpp.StanzaType
import java.util.regex.Matcher;

/**
 * Class implements retrieving user avatar from VCard
 * HTTP GET request for /rest/avatar/user@domain will return avatar for jid user@domain retrived from VCard
 */
class AvatarHandler extends tigase.http.rest.Handler {

	def DATA_PATTERN = /data:(.+);base64,(.+)/
	
    public AvatarHandler() {
		description = [
			regex : "/{user_jid}/{source}",
			GET : [ info:'Retrieve user avatar', 
				description: """Retrieves avatar of user passed as {user_jid} parameter of url. Avatar is returned in binary form, so this might be used to present user avatar on web pages, etc.
Additional parameter {source} may be passed to specify source of avatar.  It may be:
-avatar
-vcard4
-vcard-temp
If {source} is not specified server will return first avatar found while searching this list.
"""]
		]
        regex = /\/(?:([^@\/]+)@){0,1}([^@\/]+)\/?(avatar|vcard4|vcard-temp)?/
        isAsync = true
		execGet = { Service service, callback, String localPart, String domain, String source ->
			if (source == null) {
				retrieveFromAvatar(service, { avatarPep ->
					if (avatarPep == null)
						retrieveFromVCard4(service, { avatarVCard4 ->
								if (avatarVCard4 == null)
									retrieveFromVCardTemp(service, callback, localPart, domain);
								else 
								callback(avatarVCard4);
						}, localPart, domain);
					else
						callback(avatarPep);
				}, localPart, domain);
			} else {
				switch (source) {
					case "avatar":
						retrieveFromAvatar(service, callback, localPart, domain);
						break;
					case "vcard4":
						retrieveFromVCard4(service, callback, localPart, domain);
						break;
					case "vcard-temp":
						retrieveFromVCardTemp(service, callback, localPart, domain);
						break;
				}
			}
			
		}
    }
	
	private void retrieveFromAvatar(Service service, def callback, String localPart, String domain) {
		Element iq = new Element("iq");
		iq.setAttribute("from", localPart != null ? "$localPart@$domain" : domain);
		iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
		iq.setAttribute("type", "get");
		
		Element pubsub = new Element("pubsub");
		pubsub.setXMLNS("http://jabber.org/protocol/pubsub");
		iq.addChild(pubsub);
		
		Element items = new Element("items");
		items.setAttribute("node", "urn:xmpp:avatar:metadata")
		items.setAttribute("max_items", "1");
		pubsub.addChild(items);	
		
		service.sendPacket(new Iq(iq), 30, { result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }
				
                def infos = result.getElement().getChildren().find { it.getName() == "pubsub" && it.getXMLNS() == "http://jabber.org/protocol/pubsub" }?.getChildren()?.find { it.getName() == "items" }?.getChildren()?.find { it.getName() == "item" }?.getChildren()?.find { it.getName() == "metadata" }?.getChildren()?.findAll { it.getName() == "info" };
                if (infos == null || infos.isEmpty()) {
                    callback(null);
                    return;
                }
				
				def info = infos.get(0);
				def id = info.getAttribute("id");
				def type = info.getAttribute("type");
				
				iq = new Element("iq");
				iq.setAttribute("from", localPart != null ? "$localPart@$domain" : domain);
				iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
				iq.setAttribute("type", "get");
		
				pubsub = new Element("pubsub");
				pubsub.setXMLNS("http://jabber.org/protocol/pubsub");
				iq.addChild(pubsub);
		
				items = new Element("items");
				items.setAttribute("node", "urn:xmpp:avatar:data")
				pubsub.addChild(items);
				
				Element item = new Element("item");
				item.setAttribute("id", id);
				items.addChild(item);
				
				service.sendPacket(new Iq(iq), 30, { resultData ->
					if (resultData == null || resultData.getType() == StanzaType.error) {
						callback(null);
						return;
					}
				
					def data = resultData.getElement().getChildren().find { it.getName() == "pubsub" && it.getXMLNS() == "http://jabber.org/protocol/pubsub" }?.getChildren()?.find { it.getName() == "items" }?.getChildren()?.find { it.getName() == "item" }?.getChildren()?.find { it.getName() == "data" }?.getCData();
						
					if  (!data) {
						callback(null);
						return;
					}
						
					def outResult = new tigase.http.rest.Handler.Result();
					outResult.contentType = type
					String contentBase64 = data;
					// TODO: added workaround for bad result of Base64.decode when encoded data is wrapped
					// It should be removed when issue https://projects.tigase.org/issues/1265 is fixed
					// outResult.data = Base64.decode(contentBase64);
					outResult.data = Base64.decode(contentBase64.replace('\n', '').replace('\r',''));
						
					callback(outResult);
				});
            });				
	}

	private void retrieveFromVCard4(Service service, def callback, String localPart, String domain) {
		Element iq = new Element("iq");
		iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
		iq.setAttribute("type", "get");
		
		Element vcard = new Element("vcard");
		vcard.setXMLNS("urn:ietf:params:xml:ns:vcard-4.0");
		iq.addChild(vcard);
		
		service.sendPacket(new Iq(iq), 30, { result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }
				
                def photoUri = result.getElement().getChildren().find { it.getName() == "vcard" && it.getXMLNS() == "urn:ietf:params:xml:ns:vcard-4.0" }?.getChildren()?.find { it.getName() == "photo" }?.getChildren()?.find { it.getName() == "uri" }?.getCData();
                if (photoUri == null) {
                    callback(null);
                    return;
                }
				
				Matcher matcher = (photoUri.replace('\n', '').replace('\r','')) =~ DATA_PATTERN
				if (!matcher.matches()) {
					callback({ req, resp ->
						resp.sendRedirect(photoUri);
					});
                    return;
				}
				
                def outResult = new tigase.http.rest.Handler.Result();
                outResult.contentType = matcher.group(1);
                String contentBase64 = matcher.group(2);
                // TODO: added workaround for bad result of Base64.decode when encoded data is wrapped
                // It should be removed when issue https://projects.tigase.org/issues/1265 is fixed
                // outResult.data = Base64.decode(contentBase64);
                outResult.data = Base64.decode(contentBase64.replace('\n', '').replace('\r',''));
				
                callback(outResult);
            });		
	}
	
	private void retrieveFromVCardTemp(Service service, def callback, String localPart, String domain) {
		Element iq = new Element("iq");
		iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
		iq.setAttribute("type", "get");
		
		Element vcard = new Element("vCard");
		vcard.setXMLNS("vcard-temp");
		iq.addChild(vcard);
		
		service.sendPacket(new Iq(iq), 30, { result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }
				
                def photo = result.getElement().getChildren().find { it.getName() == "vCard" && it.getXMLNS() == "vcard-temp" }?.getChildren().find { it.getName() == "PHOTO" };
                if (photo == null) {
                    callback(null);
                    return;
                }
				
				def extval = photo.getChildren().find { it.getName() == "EXTVAL" }?.getCData()
				if (extval) {
					callback({ req, resp ->
						resp.sendRedirect(extval);
					});
				} else {
					def outResult = new tigase.http.rest.Handler.Result();
					outResult.contentType = photo.getChildren().find { it.getName() == "TYPE" }?.getCData()
					String contentBase64 = photo.getChildren().find { it.getName() == "BINVAL" }?.getCData();
					// TODO: added workaround for bad result of Base64.decode when encoded data is wrapped
					// It should be removed when issue https://projects.tigase.org/issues/1265 is fixed
					// outResult.data = Base64.decode(contentBase64);
					outResult.data = Base64.decode(contentBase64.replace('\n', '').replace('\r',''));
					
					callback(outResult);
				}
            });		
	}

}