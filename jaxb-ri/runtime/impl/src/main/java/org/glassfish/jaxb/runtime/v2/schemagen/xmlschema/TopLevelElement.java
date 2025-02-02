/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jaxb.runtime.v2.schemagen.xmlschema;

import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;

import javax.xml.namespace.QName;

/**
 * <p><b>
 *     Auto-generated, do not edit.
 * </b></p>
 */
@XmlElement("element")
public interface TopLevelElement
    extends Element, TypedXmlWriter
{


    @XmlAttribute("final")
    TopLevelElement _final(String[] value);

    @XmlAttribute("final")
    TopLevelElement _final(String value);

    @XmlAttribute("abstract")
    TopLevelElement _abstract(boolean value);

    @XmlAttribute
    TopLevelElement substitutionGroup(QName value);

    @XmlAttribute
    TopLevelElement name(String value);

}
