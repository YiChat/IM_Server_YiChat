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
import tigase.xmpp.BareJID
import tigase.util.Base64;
import tigase.xml.DomBuilderHandler
import tigase.xml.SimpleParser
import tigase.xml.Element
import tigase.xml.SingletonFactory

/**
 * Class implements ability to manage users for service administrator
 * Handles requests for /rest/user/user@domain where user@domain is jid
 *
 * Example format of content of request or response:
 * <user><jid>user@domain</jid><password>Paa$$w0rd</password></jid></user>
 */
class AvatarPutNoAuthHandler extends tigase.http.rest.Handler {

    public AvatarPutNoAuthHandler() {
		description = [
			regex : "/{user_jid}",
			PUT : [ info:'Change user avatar', 
				description: """Changes avatar of user passed as {user_jid} parameter of url. As content of request binary form of image should be passed as it will be used as new avatar of a user.
"""]
		]
        regex = /\/(?:([^@\/]+)@){0,1}([^@\/]+)/
        isAsync = false
		decodeContent = false
        execPut = { Service service, callback, request, localPart, domain ->
            def jid = BareJID.bareJIDInstance(localPart, domain);
			
			InputStream is = request.getInputStream();
			byte[] data = new byte[3*1024*1024];
			int size = 0;
			int read = 0;
			while ((read = is.read(data, size, data.length - size)) != -1) {
				size += read;
			}
			
			data = Arrays.copyOf(data, size);
			
			String mimeType = request.getContentType();
			String encodedPhoto = Base64.encode(data);
			
			def userRepo = service.getUserRepository();
			
			String vCardStr = userRepo.getData(jid, "public/vcard-temp", "vCard", null);
			def vCardEl = null;
			def parsed = null;
			
			if (vCardStr != null && !vCardStr.isEmpty()) {
				SimpleParser parser   = SingletonFactory.getParserInstance();
				DomBuilderHandler domHandler = new DomBuilderHandler();
				def vCardData = vCardStr.toCharArray()
				parser.parse(domHandler, vCardData, 0, vCardData.length);			
				parsed = domHandler.getParsedElements();
			}
				
			if (parsed != null && !parsed.isEmpty()) {
				vCardEl = parsed.poll();
			}
			else {
				vCardEl = new Element("vCard");
				vCardEl.setXMLNS("vcard-temp");
			}
			
			Element photoEl = vCardEl.getChild("PHOTO");
			if (photoEl == null) {
				photoEl = new Element("PHOTO");
				vCardEl.addChild(photoEl);
			}
			
			Element typeEl = photoEl.getChild("TYPE");
			if (typeEl != null) {
				photoEl.removeChild(typeEl);
			}
			typeEl = new Element("TYPE");
			photoEl.addChild(typeEl);
			typeEl.setCData(mimeType);
			
			Element binvalEl = photoEl.getChild("BINVAL");
			if (binvalEl != null) {
				photoEl.removeChild(binvalEl);
			}
			binvalEl = new Element("BINVAL");
			photoEl.addChild(binvalEl);
			binvalEl.setCData(encodedPhoto);
			
			userRepo.setData(jid, "public/vcard-temp", "vCard", vCardEl.toString());
			
            callback([user:[jid:(localPart != null ? "$localPart@$domain" : domain)]]);
        }
    }

}