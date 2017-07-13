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
import tigase.xml.Element
import tigase.xmpp.BareJID
import tigase.xmpp.JID
import tigase.xmpp.StanzaType

import java.util.logging.Logger
import java.util.logging.Level

/**
 * Class implements generic support for ad-hoc commands
 */
class AdHocHandler extends tigase.http.rest.Handler {

	private static final Logger log = Logger.getLogger(AdHocHandler.class.getCanonicalName());
	
    def TIMEOUT = 30 * 1000;

    def COMMAND_XMLNS = "http://jabber.org/protocol/commands";
    def DATA_XMLNS = "jabber:x:data";
    def DISCO_ITEMS_XMLNS = "http://jabber.org/protocol/disco#items";

    public AdHocHandler() {
		description = [
			regex : "/{component_jid}",
			GET : [ info:'List available adhoc commands', 
				description: """Retrieves list of available adhoc commands to execute on component which is running as {component_jid}, where {component_jid} may be Tigase XMPP Server internal component jid.

Example result:
\${util.formatData([items:[[jid:'sess-man@domain.com',node:'http://jabber.org/protocol/admin#get-active-users',name:'Get list of active users'],[jid:'sess-man@domain.com',node:'del-script',name:'Remove command script'],[jid:'sess-man@domain.com',node:'add-script',name:'New command script']]])}
"""],
			POST : [ info:'Execute adhoc command',
				description: """To execute adhoc command you need to provide proper {component_jid} and also pass additional data in form of XML or JSON as ie. to execute Get list of active users command you need to pass following XML:
\${util.formatData([command:[node:'http://jabber.org/protocol/admin#get-active-users',fields:[[var:'domainjid',value:'domain.com'],[var:'max_items',value:'25']]]])}

In result of this operation you will receive ie. following XML:
\${util.formatData([command:[jid:'sess-man@domain.com',node:'http://jabber.org/protocol/admin#get-active-users',fields:[[var:'Users: 2',label:'text-multi',value:['user1@domain.com','user2@domain.com']]]]])}
"""]
		];
		regex = /\/(?:([^@\/]+)@){0,1}([^@\/]+)/
        requiredRole = "admin"
        isAsync = true

        /**
         * Handles GET request and returns list of available ad-hoc commands
         */
        execGet = { Service service, callback, user, localPart, domain ->

            Element iq = new Element("iq");
            iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
            iq.setAttribute("from", user.toString());
            iq.setAttribute("type", "get");
            iq.setAttribute("id", UUID.randomUUID().toString())

            Element query = new Element("query");
            query.setXMLNS(DISCO_ITEMS_XMLNS);
            query.setAttribute("node", COMMAND_XMLNS);
            iq.addChild(query);

            service.sendPacket(new Iq(iq), TIMEOUT, { Packet result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }

                def items = result.getElement().getChild("query", DISCO_ITEMS_XMLNS).getChildren().findAll({ it.getName() == "item" })
                items = items.collect { [jid: it.getAttribute("jid"), node: it.getAttribute("node"), name: it.getAttribute("name")] }

                def results = [items:items];
                callback(results)
            });
        }

        /**
         * Handles POST request, executes ad-hoc command using data sent in POST content
         * and returns result of execution
         */
        execPost = { Service service, callback, user, content, localPart, domain ->
            def compJid = BareJID.bareJIDInstance(localPart, domain);

            content = content.command;
            def node = content.node;
            if (!node) {
                callback(null);
                return;
            }

            def fields = content.fields;

            Element iq = new Element("iq");
            iq.setAttribute("to", localPart != null ? "$localPart@$domain" : domain);
            iq.setAttribute("from", user.toString());
            iq.setAttribute("type", "set");
            iq.setAttribute("id", UUID.randomUUID().toString())

            Element command = new Element("command");
            command.setXMLNS(COMMAND_XMLNS);
            command.setAttribute("node", node);
            iq.addChild(command);

            if (fields) {
                Element x = new Element("x");
                x.setXMLNS(DATA_XMLNS);
                x.setAttribute("type", "submit");
                command.addChild(x);

                if (log.isLoggable(Level.FINEST)) {
                  log.finest("adhoc fields: " + fields);
                }

               

                fields.each { field ->
                    Element fieldEl = new Element("field");
                    fieldEl.setAttribute("var", field.var);
                    x.addChild(fieldEl);

                    if (field.value) {
                        if (field.value instanceof Collection) {
                            field.value.each { val ->
                                Element valueEl = new Element("value", val);
                                fieldEl.addChild(valueEl);
                            }
                        }
                        else {
                            Element valueEl = new Element("value", field.value);
                            fieldEl.addChild(valueEl);
                        }
                    }
                }
            }

            service.sendPacket(new Iq(iq), TIMEOUT, { Packet result ->
                if (result == null || result.getType() == StanzaType.error) {
                    callback(null);
                    return;
                }

                command = result.getElement().getChild("command", COMMAND_XMLNS);
                def data = command.getChild("x", DATA_XMLNS);
                def fieldElems = data.getChildren().findAll({ it.getName() == "field"});

                fields = [];
                def results = [jid: (localPart != null ? "$localPart@$domain" : domain), node: node, fields:fields];

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
                        field.type = fieldEl.getAttribute("type");
                    }
                    fields.add(field);

                    def valueElems = fieldEl.getChildren().findAll({ it.getName() == "value" });

//  edit by cai     
//                    if (valueElems.size() > 0) {
//                        field.value = [];
//                        valueElems.each { valueEl ->
//                            field.value.add(valueEl.getCData());
//                        }
//                    }


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
				// add by cai
				// parse groups info
				def fieldsElem = data.getChild("fields");
				if (fieldsElem != null) {
					fieldsElem.getChildren().each { childElem ->
						
						def field = new LinkedHashMap<String, String>();
						childElem.getChildren().each { elem ->
							field.(elem.getName()) = elem.getCData();
						}
						fields.add(field)
					}
				}
				
				def tables = [];
				def table = null;
				data.getChildren().each { child ->
					if (!(child.getName() == "reported" || child.getName() == "item"))
						return;
					if (child.getName() == "reported") {
						table = [label:child.getAttribute("label"), items:[]];
						tables.add(table);
						return;
					}
					if (table == null)
						return;
					def item = []
					child.getChildren().each { fieldEl ->
						def value = fieldEl.getChildren().findAll({ it.getName() == "value" });
						if (value.size() > 0) {
							value = value.get(0).getCData();
						} else {
							value = null;
						}
						item.add([var:fieldEl.getAttribute("var"), value:value])
					}
						
					table.items.add([fields:item]);
				}
				if (!tables.isEmpty()) {
					results.reported = tables;
				}

                callback([command:results])
            });
        }
    }

}
