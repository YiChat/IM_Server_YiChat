package tigase.rest.adhoc

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
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.Base64
import tigase.util.DNSResolverFactory
import tigase.xml.Element
import tigase.xmpp.BareJID
import tigase.xmpp.JID
import tigase.xmpp.StanzaType

/**
 * Class implements support for retrieving component statistics
 */
class ComponentStatsHandler extends tigase.http.rest.Handler {

    def TIMEOUT = 30 * 1000;

    def COMMAND_XMLNS = "http://jabber.org/protocol/commands";
    def DATA_XMLNS = "jabber:x:data";
    def DISCO_ITEMS_XMLNS = "http://jabber.org/protocol/disco#items";

    public ComponentStatsHandler() {
		description = [
			regex : "/{component_jid}",
			GET : [ info:'Retrieve statistics of component', 
				description: """Retrieves statistics of a component which jid is passed in url as {component_jid} and returns them in form of XML or JSON depending on passed Accept HTTP request header.

Example partial response for sess-man component:
\${util.formatData([stats:[component:'sess-man',node:'stats/sess-man',data:[[var:'sess-man/Registered accounts',value:292],[var:'sess-man/Open user connections',value:10]]]])}
"""]
		]
		regex = /\/([^@\/]+)/
        requiredRole = "admin"
        isAsync = true

        /**
         * Handles GET request and returns list of available ad-hoc commands
         */
        execGet = { Service service, callback, user, compName ->

			String domain = DNSResolverFactory.getInstance().getDefaultHost();
			String node = "stats/" + compName;
			
            Element iq = new Element("iq");
            iq.setAttribute("to", "stats@$domain");
            iq.setAttribute("from", user.toString());
            iq.setAttribute("type", "get");
            iq.setAttribute("id", UUID.randomUUID().toString())

            Element command = new Element("command");
            command.setXMLNS(COMMAND_XMLNS);
            command.setAttribute("node", node);
			iq.addChild(command);

            Element x = new Element("x");
            x.setXMLNS(DATA_XMLNS);
            x.setAttribute("type", "submit");
            command.addChild(x);
            Element qfieldEl = new Element("field");
            qfieldEl.setAttribute("var", 'Stats level');
            x.addChild(qfieldEl);
			qfieldEl.addChild(new Element("value", 'FINEST'));
			
            service.sendPacket(new Iq(iq), TIMEOUT, { Packet result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }

                command = result.getElement().getChild("command", COMMAND_XMLNS);
                def data = command.getChild("x", DATA_XMLNS);
                def fieldElems = data.getChildren().findAll({ it.getName() == "field"});

                def fields = [];
                def results = [component: compName , node: node, data:fields];

                def titleEl = data.getChild("title");
                if (titleEl) results.title = titleEl.getCData();

                def instructionsEl = data.getChild("instructions");
                if (instructionsEl) results.instructions = instructionsEl.getCData();

                def noteEl = command.getChild("note");
                if (noteEl) {
                    results.note = [value:noteEl.getCData()];
                    if (noteEl.getAttribute("type")) {
                        results.note.type = noteEl.getAttribute("type");
                    }
                }

                fieldElems.each { fieldEl ->
                    def field = [var:fieldEl.getAttribute("var")];

                    if (fieldEl.getAttribute("label")) {
                        field.label = fieldEl.getAttribute("label");
                    }

                    if (fieldEl.getAttribute("type")) {
                        field.label = fieldEl.getAttribute("type");
                    }
                    fields.add(field);

                    def valueElems = fieldEl.getChildren().findAll({ it.getName() == "value" });
                    if (valueElems.size() == 1) {
                         field.value = valueElems.get(0).getCData();
                    }
                    else if (valueElems.size() > 1) {
                        field.value = [];
                        valueElems.each { valueEl ->
                            field.value.add(valueEl.getCData());
                        }
                    }

                    def optionElems = fieldEl.getChildren().findAll({ it.getName() == "option" });
                    if (!optionElems.isEmpty()) {
                        field.options = [];
                        optionElems.each { optionEl ->
                            def item = [value:optionEl.getChild("value").getCData()];
                            if (optionEl.getAttribute("label")) item.label = optionEl.getAttribute("label");
                            field.options.add(item);
                        }
                    }
                }

                callback([stats:results])					
            });
        }
	}

}