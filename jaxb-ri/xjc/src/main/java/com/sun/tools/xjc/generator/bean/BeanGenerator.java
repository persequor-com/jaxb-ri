/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.tools.xjc.generator.bean;

import static com.sun.tools.xjc.outline.Aspect.EXPOSED;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sun.tools.xjc.outline.ElementOutline;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlAttachmentRef;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlMimeType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JAnnotatable;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JEnumConstant;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JJavaName;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.fmt.JStaticJavaFile;
import com.sun.tools.xjc.AbortException;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.generator.annotation.spec.XmlAnyAttributeWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlEnumValueWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlEnumWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlJavaTypeAdapterWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlMimeTypeWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlRootElementWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlSeeAlsoWriter;
import com.sun.tools.xjc.generator.annotation.spec.XmlTypeWriter;
import com.sun.tools.xjc.generator.bean.field.FieldRenderer;
import com.sun.tools.xjc.model.CAdapter;
import com.sun.tools.xjc.model.CAttributePropertyInfo;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CClassInfoParent;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.CEnumConstant;
import com.sun.tools.xjc.model.CEnumLeafInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.CTypeRef;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.model.CClassRef;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.EnumConstantOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;
import com.sun.tools.xjc.util.CodeModelClassFactory;
import org.glassfish.jaxb.core.v2.model.core.PropertyInfo;
import org.glassfish.jaxb.core.v2.runtime.SwaRefAdapterMarker;
import com.sun.xml.xsom.XmlString;
import com.sun.istack.NotNull;
import com.sun.tools.xjc.model.CReferencePropertyInfo;

/**
 * Generates fields and accessors.
 */
public final class BeanGenerator implements Outline {

    /** JAXB module name. JAXB dependency is mandatory in generated Java module. */
    private static final String JAXB_PACKAGE = "jakarta.xml.bind";

    /** Simplifies class/interface creation and collision detection. */
    private final CodeModelClassFactory codeModelClassFactory;
    private final ErrorReceiver errorReceiver;
    /** all {@link PackageOutline}s keyed by their {@link PackageOutline#_package}. */
    private final Map<JPackage, PackageOutlineImpl> packageContexts = new LinkedHashMap<>();
    /** all {@link ClassOutline}s keyed by their {@link ClassOutline#target}. */
    private final Map<CClassInfo, ClassOutlineImpl> classes = new LinkedHashMap<>();
    /** all {@link EnumOutline}s keyed by their {@link EnumOutline#target}. */
    private final Map<CEnumLeafInfo, EnumOutline> enums = new LinkedHashMap<>();
    /**
     * Generated runtime classes.
     */
    private final Map<Class<?>, JClass> generatedRuntime = new LinkedHashMap<>();
    /** the model object which we are processing. */
    private final Model model;
    private final JCodeModel codeModel;
    /**
     * for each property, the information about the generated field.
     */
    private final Map<CPropertyInfo, FieldOutline> fields = new LinkedHashMap<>();
    /**
     * elements that generate classes to the generated classes.
     */
    /*package*/ final Map<CElementInfo, ElementOutlineImpl> elements = new LinkedHashMap<>();

    /**
     * Generates beans into code model according to the BGM,
     * and produces the reflection model.
     *
     * @param _errorReceiver
     *      This object will receive all the errors discovered
     *      during the back-end stage.
     *
     * @return
     *      returns a {@link Outline} which will in turn
     *      be used to further generate marshaller/unmarshaller,
     *      or null if the processing fails (errors should have been
     *      reported to the error recevier.)
     */
    public static Outline generate(Model model, ErrorReceiver _errorReceiver) {

        try {
            return new BeanGenerator(model, _errorReceiver);
        } catch (AbortException e) {
            return null;
        }
    }

    private BeanGenerator(Model _model, ErrorReceiver _errorReceiver) {

        this.model = _model;
        this.codeModel = model.codeModel;
        this.errorReceiver = _errorReceiver;
        this.codeModelClassFactory = new CodeModelClassFactory(errorReceiver);

        // build enum classes
        for (CEnumLeafInfo p : model.enums().values()) {
            enums.put(p, generateEnumDef(p));
        }

        JPackage[] packages = getUsedPackages(EXPOSED);

        // generates per-package code and remember the results as contexts.
        for (JPackage pkg : packages) {
            getPackageContext(pkg);
        }

        // create the class definitions for all the beans first.
        // this should also fill in PackageContext#getClasses
        for (CClassInfo bean : model.beans().values()) {
            getClazz(bean);
        }

        // compute the package-level setting
        for (PackageOutlineImpl p : packageContexts.values()) {
            p.calcDefaultValues();
        }

        JClass OBJECT = codeModel.ref(Object.class);

        // inheritance relationship needs to be set before we generate fields, or otherwise
        // we'll fail to compute the correct type signature (namely the common base type computation)
        for (ClassOutlineImpl cc : getClasses()) {

            // setup inheritance between implementation hierarchy.
            CClassInfo superClass = cc.target.getBaseClass();
            if (superClass != null) {
                // use the specified super class
                model.strategy._extends(cc, getClazz(superClass));
            } else {
                CClassRef refSuperClass = cc.target.getRefBaseClass();
                if (refSuperClass != null) {
                    cc.implClass._extends(refSuperClass.toType(this, EXPOSED));
                } else {
                    // use the default one, if any
                    if (model.rootClass != null && cc.implClass._extends().equals(OBJECT)) {
                        cc.implClass._extends(model.rootClass);
                    }
                    if (model.rootInterface != null) {
                        cc.ref._implements(model.rootInterface);
                    }
                }
            }

            // if serialization support is turned on, generate
            // [RESULT]
            // class ... implements Serializable {
            //     private static final long serialVersionUID = <id>;
            //     ....
            // }
            if (model.serializable) {
                cc.implClass._implements(Serializable.class);
                if (model.serialVersionUID != null) {
                    cc.implClass.field(
                            JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
                            codeModel.LONG,
                            "serialVersionUID",
                            JExpr.lit(model.serialVersionUID));
                }
            }

            CClassInfoParent base = cc.target.parent();
            if ((base instanceof CClassInfo)) {
                String pkg = base.getOwnerPackage().name();
                String shortName = base.fullName().substring(base.fullName().indexOf(pkg)+pkg.length()+1);
                if (cc.target.shortName.equals(shortName)) {
                    getErrorReceiver().error(cc.target.getLocator(), Messages.ERR_KEYNAME_COLLISION.format(shortName));
                }
            }

        }

        // fill in implementation classes
        for (ClassOutlineImpl co : getClasses()) {
            generateClassBody(co);
        }

        for (EnumOutline eo : enums.values()) {
            generateEnumBody(eo);
        }

        // create factories for the impl-less elements
        for (CElementInfo ei : model.getAllElements()) {
            getPackageContext(ei._package()).objectFactoryGenerator().populate(ei);
        }

        if (model.options.getModuleName() != null) {
            codeModel._prepareModuleInfo(model.options.getModuleName(), JAXB_PACKAGE);
        }

        if (model.options.debugMode) {
            generateClassList();
        }
    }

    /**
     * Generates a class that knows how to create an instance of JAXBContext
     *
     * <p>
     * This is used in the debug mode so that a new properly configured
     * {@link JAXBContext} object can be used.
     */
    private void generateClassList() {
        try {
            JDefinedClass jc = codeModel.rootPackage()._class("JAXBDebug");
            JMethod m = jc.method(JMod.PUBLIC | JMod.STATIC, JAXBContext.class, "createContext");
            JVar $classLoader = m.param(ClassLoader.class, "classLoader");
            m._throws(JAXBException.class);
            JInvocation inv = codeModel.ref(JAXBContext.class).staticInvoke("newInstance");
            m.body()._return(inv);

            switch (model.strategy) {
                case INTF_AND_IMPL: {
                    StringBuilder buf = new StringBuilder();
                    for (PackageOutlineImpl po : packageContexts.values()) {
                        if (buf.length() > 0) {
                            buf.append(':');
                        }
                        buf.append(po._package().name());
                    }
                    inv.arg(buf.toString()).arg($classLoader);
                    break;
                }
                case BEAN_ONLY:
                    for (ClassOutlineImpl cc : getClasses()) {
                        inv.arg(cc.implRef.dotclass());
                    }
                    for (PackageOutlineImpl po : packageContexts.values()) {
                        inv.arg(po.objectFactory().dotclass());
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (JClassAlreadyExistsException e) {
            e.printStackTrace();
            // after all, we are in the debug mode. a little sloppiness is OK.
            // this error is not fatal. just continue.
        }
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public JCodeModel getCodeModel() {
        return codeModel;
    }

    @Override
    public JClassContainer getContainer(CClassInfoParent parent, Aspect aspect) {
        CClassInfoParent.Visitor<JClassContainer> v;
        switch (aspect) {
            case EXPOSED:
                v = exposedContainerBuilder;
                break;
            case IMPLEMENTATION:
                v = implContainerBuilder;
                break;
            default:
                assert false;
                throw new IllegalStateException();
        }
        return parent.accept(v);
    }

    @Override
    public JType resolve(CTypeRef ref, Aspect a) {
    	return (null != ref.getTarget()) ? ref.getTarget().getType().toType(this, a) : null;
    }
    private final CClassInfoParent.Visitor<JClassContainer> exposedContainerBuilder =
            new CClassInfoParent.Visitor<>() {

                @Override
                public JClassContainer onBean(CClassInfo bean) {
                    return getClazz(bean).ref;
                }

                @Override
                public JClassContainer onElement(CElementInfo element) {
                    // hmm...
                    return getElement(element).implClass;
                }

                @Override
                public JClassContainer onPackage(JPackage pkg) {
                    return model.strategy.getPackage(pkg, EXPOSED);
                }
            };
    private final CClassInfoParent.Visitor<JClassContainer> implContainerBuilder =
            new CClassInfoParent.Visitor<>() {

                @Override
                public JClassContainer onBean(CClassInfo bean) {
                    return getClazz(bean).implClass;
                }

                @Override
                public JClassContainer onElement(CElementInfo element) {
                    return getElement(element).implClass;
                }

                @Override
                public JClassContainer onPackage(JPackage pkg) {
                    return model.strategy.getPackage(pkg, Aspect.IMPLEMENTATION);
                }
            };

    /**
     * Returns all <i>used</i> JPackages.
     *
     * A JPackage is considered as "used" if a ClassItem or
     * a InterfaceItem resides in that package.
     *
     * This value is dynamically calculated every time because
     * one can freely remove ClassItem/InterfaceItem.
     *
     * @return
     *         Given the same input, the order of packages in the array
     *         is always the same regardless of the environment.
     */
    public JPackage[] getUsedPackages(Aspect aspect) {
        Set<JPackage> s = new TreeSet<>();

        for (CClassInfo bean : model.beans().values()) {
            JClassContainer cont = getContainer(bean.parent(), aspect);
            if (cont.isPackage()) {
                s.add((JPackage) cont);
            }
        }

        for (CElementInfo e : model.getElementMappings(null).values()) {
            // at the first glance you might think we should be iterating all elements,
            // not just global ones, but if you think about it, local ones live inside
            // another class, so those packages are already enumerated when we were
            // walking over CClassInfos.
            s.add(e._package());
        }

        return s.toArray(new JPackage[0]);
    }

    @Override
    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    @Override
    public CodeModelClassFactory getClassFactory() {
        return codeModelClassFactory;
    }

    @Override
    public PackageOutlineImpl getPackageContext(JPackage p) {
        PackageOutlineImpl r = packageContexts.get(p);
        if (r == null) {
            r = new PackageOutlineImpl(this, model, p);
            packageContexts.put(p, r);
        }
        return r;
    }

    /**
     * Generates the minimum {@link JDefinedClass} skeleton
     * without filling in its body.
     */
    private ClassOutlineImpl generateClassDef(CClassInfo bean) {
        ImplStructureStrategy.Result r = model.strategy.createClasses(this, bean);
        JClass implRef;

        if (bean.getUserSpecifiedImplClass() != null) {
            // create a place holder for a user-specified class.
            JDefinedClass usr;
            try {
                usr = codeModel._class(bean.getUserSpecifiedImplClass());
                // but hide that file so that it won't be generated.
                usr.hide();
            } catch (JClassAlreadyExistsException e) {
                // it's OK for this to collide.
                usr = e.getExistingClass();
            }
            usr._extends(r.implementation);
            implRef = usr;
        } else {
            implRef = r.implementation;
        }

        return new ClassOutlineImpl(this, bean, r.exposed, r.implementation, implRef);
    }

    @Override
    public Collection<ClassOutlineImpl> getClasses() {
        // make sure that classes are fully populated
        assert model.beans().size() == classes.size();
        return classes.values();
    }

    @Override
    public ClassOutlineImpl getClazz(CClassInfo bean) {
        ClassOutlineImpl r = classes.get(bean);
        if (r == null) {
            classes.put(bean, r = generateClassDef(bean));
        }
        return r;
    }

    @Override
    public ElementOutline getElement(CElementInfo ei) {
        ElementOutline def = elements.get(ei);
        if (def == null && ei.hasClass()) {
            // create one. in the constructor it adds itself to the elements.
            def = new ElementOutlineImpl(this, ei);
        }
        return def;
    }

    @Override
    public EnumOutline getEnum(CEnumLeafInfo eli) {
        return enums.get(eli);
    }

    @Override
    public Collection<EnumOutline> getEnums() {
        return enums.values();
    }

    @Override
    public Iterable<? extends PackageOutline> getAllPackageContexts() {
        return packageContexts.values();
    }

    @Override
    public FieldOutline getField(CPropertyInfo prop) {
        return fields.get(prop);
    }

    /**
     * Generates the body of a class.
     *
     */
    private void generateClassBody(ClassOutlineImpl cc) {
        CClassInfo target = cc.target;

        // used to simplify the generated annotations
        String mostUsedNamespaceURI = cc._package().getMostUsedNamespaceURI();

        // [RESULT]
        // @XmlType(name="foo", targetNamespace="bar://baz")
        XmlTypeWriter xtw = cc.implClass.annotate2(XmlTypeWriter.class);
        writeTypeName(cc.target.getTypeName(), xtw, mostUsedNamespaceURI);

        // @XmlSeeAlso
        Iterator<CClassInfo> subclasses = cc.target.listSubclasses();
        if (subclasses.hasNext()) {
            XmlSeeAlsoWriter saw = cc.implClass.annotate2(XmlSeeAlsoWriter.class);
            while (subclasses.hasNext()) {
                CClassInfo s = subclasses.next();
                saw.value(getClazz(s).implRef);
            }
        }

        if (target.isElement()) {
            String namespaceURI = target.getElementName().getNamespaceURI();
            String localPart = target.getElementName().getLocalPart();

            // [RESULT]
            // @XmlRootElement(name="foo", targetNamespace="bar://baz")
            XmlRootElementWriter xrew = cc.implClass.annotate2(XmlRootElementWriter.class);
            xrew.name(localPart);
            if (!namespaceURI.equals(mostUsedNamespaceURI)) // only generate if necessary
            {
                xrew.namespace(namespaceURI);
            }
        }

        if (target.isOrdered()) {
            for (CPropertyInfo p : target.getProperties()) {
                if (!(p instanceof CAttributePropertyInfo)) {
                    if (!((p instanceof CReferencePropertyInfo)
                            && ((CReferencePropertyInfo) p).isDummy())) {
                        xtw.propOrder(p.getName(false));
                    }
                }
            }
        } else {
            // produce empty array
            xtw.getAnnotationUse().paramArray("propOrder");
        }

        for (CPropertyInfo prop : target.getProperties()) {
            generateFieldDecl(cc, prop);
        }

        if (target.declaresAttributeWildcard()) {
            generateAttributeWildcard(cc);
        }

        // generate some class level javadoc
        cc.ref.javadoc().append(target.javadoc);

        cc._package().objectFactoryGenerator().populate(cc);
    }

    private void writeTypeName(QName typeName, XmlTypeWriter xtw, String mostUsedNamespaceURI) {
        if (typeName == null) {
            xtw.name("");
        } else {
            xtw.name(typeName.getLocalPart());
            final String typeNameURI = typeName.getNamespaceURI();
            if (!typeNameURI.equals(mostUsedNamespaceURI)) // only generate if necessary
            {
                xtw.namespace(typeNameURI);
            }
        }
    }

    /**
     * Generates an attribute wildcard property on a class.
     */
    private void generateAttributeWildcard(ClassOutlineImpl cc) {
        String FIELD_NAME = "otherAttributes";
        String METHOD_SEED = model.getNameConverter().toClassName(FIELD_NAME);

        JClass mapType = codeModel.ref(Map.class).narrow(QName.class, String.class);
        JClass mapImpl = codeModel.ref(HashMap.class).narrow(QName.class, String.class);

        // [RESULT]
        // Map<QName,String> m = new HashMap<QName,String>();
        JFieldVar $ref = cc.implClass.field(JMod.PRIVATE,
                mapType, FIELD_NAME, JExpr._new(mapImpl));
        $ref.annotate2(XmlAnyAttributeWriter.class);

        MethodWriter writer = cc.createMethodWriter();

        JMethod $get = writer.declareMethod(mapType, "get" + METHOD_SEED);
        $get.javadoc().append(
                "Gets a map that contains attributes that aren't bound to any typed property on this class.\n\n"
                + "<p>\n"
                + "the map is keyed by the name of the attribute and \n"
                + "the value is the string value of the attribute.\n"
                + "\n"
                + "the map returned by this method is live, and you can add new attribute\n"
                + "by updating the map directly. Because of this design, there's no setter.\n");
        $get.javadoc().addReturn().append("always non-null");

        $get.body()._return($ref);
    }

    /**
     * Generates the minimum {@link JDefinedClass} skeleton
     * without filling in its body.
     */
    private EnumOutline generateEnumDef(CEnumLeafInfo e) {
        JDefinedClass type;

        type = getClassFactory().createClass(
                getContainer(e.parent, EXPOSED), e.shortName, e.getLocator(), ClassType.ENUM);
        type.javadoc().append(e.javadoc);

        return new EnumOutline(e, type) {

            @Override
            public
            @NotNull
            Outline parent() {
                return BeanGenerator.this;
            }
        };
    }

    private void generateEnumBody(EnumOutline eo) {
        JDefinedClass type = eo.clazz;
        CEnumLeafInfo e = eo.target;

        XmlTypeWriter xtw = type.annotate2(XmlTypeWriter.class);
        writeTypeName(e.getTypeName(), xtw,
                eo._package().getMostUsedNamespaceURI());

        JCodeModel cModel = model.codeModel;

        // since constant values are never null, no point in using the boxed types.
        JType baseExposedType = e.base.toType(this, EXPOSED).unboxify();
        JType baseImplType = e.base.toType(this, Aspect.IMPLEMENTATION).unboxify();


        XmlEnumWriter xew = type.annotate2(XmlEnumWriter.class);
        xew.value(baseExposedType);


        boolean needsValue = e.needsValueField();

        // for each member <m>,
        // [RESULT]
        //    <EnumName>(<deserializer of m>(<value>));

        Set<String> enumFieldNames = new HashSet<>();    // record generated field names to detect collision

        for (CEnumConstant mem : e.members) {
            String constName = mem.getName();

            if (!JJavaName.isJavaIdentifier(constName)) {
                // didn't produce a name.
                getErrorReceiver().error(e.getLocator(),
                        Messages.ERR_UNUSABLE_NAME.format(mem.getLexicalValue(), constName));
            }

            if (!enumFieldNames.add(constName)) {
                getErrorReceiver().error(e.getLocator(), Messages.ERR_NAME_COLLISION.format(constName));
            }

            // [RESULT]
            // <Const>(...)
            // ASSUMPTION: datatype is outline-independent
            JEnumConstant constRef = type.enumConstant(constName);
            if (needsValue) {
                constRef.arg(e.base.createConstant(this, new XmlString(mem.getLexicalValue())));
            }

            if (!mem.getLexicalValue().equals(constName)) {
                constRef.annotate2(XmlEnumValueWriter.class).value(mem.getLexicalValue());
            }

            // set javadoc
            if (mem.javadoc != null) {
                constRef.javadoc().append(mem.javadoc);
            }

            eo.constants.add(new EnumConstantOutline(mem, constRef) {
            });
        }


        if (needsValue) {
            // [RESULT]
            // final <valueType> value;
            JFieldVar $value = type.field(JMod.PRIVATE | JMod.FINAL, baseExposedType, "value");

            // [RESULT]
            // public <valuetype> value() { return value; }
            type.method(JMod.PUBLIC, baseExposedType, "value").body()._return($value);

            // [RESULT]
            // <constructor>(<valueType> v) {
            //     this.value=v;
            // }
            {
                JMethod m = type.constructor(0);
                m.body().assign($value, m.param(baseImplType, "v"));
            }

            // [RESULT]
            // public static <Const> fromValue(<valueType> v) {
            //   for( <Const> c : <Const>.values() ) {
            //       if(c.value == v)   // or equals
            //           return c;
            //   }
            //   throw new IllegalArgumentException(...);
            // }
            {
                JMethod m = type.method(JMod.PUBLIC | JMod.STATIC, type, "fromValue");
                JVar $v = m.param(baseExposedType, "v");
                JForEach fe = m.body().forEach(type, "c", type.staticInvoke("values"));
                JExpression eq;
                if (baseExposedType.isPrimitive()) {
                    eq = fe.var().ref($value).eq($v);
                } else {
                    eq = fe.var().ref($value).invoke("equals").arg($v);
                }

                fe.body()._if(eq)._then()._return(fe.var());

                JInvocation ex = JExpr._new(cModel.ref(IllegalArgumentException.class));

                JExpression strForm;
                if (baseExposedType.isPrimitive()) {
                    strForm = cModel.ref(String.class).staticInvoke("valueOf").arg($v);
                } else if (baseExposedType == cModel.ref(String.class)) {
                    strForm = $v;
                } else {
                    strForm = $v.invoke("toString");
                }
                m.body()._throw(ex.arg(strForm));
            }
        } else {
            // [RESULT]
            // public String value() { return name(); }
            type.method(JMod.PUBLIC, String.class, "value").body()._return(JExpr.invoke("name"));

            // [RESULT]
            // public <Const> fromValue(String v) { return valueOf(v); }
            JMethod m = type.method(JMod.PUBLIC | JMod.STATIC, type, "fromValue");
            m.body()._return(JExpr.invoke("valueOf").arg(m.param(String.class, "v")));
        }
    }

    /**
     * Determines the FieldRenderer used for the given FieldUse,
     * then generates the field declaration and accessor methods.
     *
     * The {@code fields} map will be updated with the newly
     * created FieldRenderer.
     */
    private FieldOutline generateFieldDecl(ClassOutlineImpl cc, CPropertyInfo prop) {
        FieldRenderer fr = prop.realization;
        if (fr == null) // none is specified. use the default factory
        {
            fr = model.options.getFieldRendererFactory().getDefault();
        }

        FieldOutline field = fr.generate(cc, prop);
        fields.put(prop, field);

        return field;
    }

    /**
     * Generates {@link XmlJavaTypeAdapter} from {@link PropertyInfo} if necessary.
     * Also generates other per-property annotations
     * (such as {@link XmlID}, {@link XmlIDREF}, and {@link XmlMimeType} if necessary.
     */
    public void generateAdapterIfNecessary(CPropertyInfo prop, JAnnotatable field) {
        CAdapter adapter = prop.getAdapter();
        if (adapter != null) {
            if (adapter.getAdapterIfKnown() == SwaRefAdapterMarker.class) {
                field.annotate(XmlAttachmentRef.class);
            } else {
                // [RESULT]
                // @XmlJavaTypeAdapter( Foo.class )
                XmlJavaTypeAdapterWriter xjtw = field.annotate2(XmlJavaTypeAdapterWriter.class);
                xjtw.value(adapter.adapterType.toType(this, EXPOSED));
            }
        }

        switch (prop.id()) {
            case ID:
                field.annotate(XmlID.class);
                break;
            case IDREF:
                field.annotate(XmlIDREF.class);
                break;
        }

        if (prop.getExpectedMimeType() != null) {
            field.annotate2(XmlMimeTypeWriter.class).value(prop.getExpectedMimeType().toString());
        }
    }

    @Override
    public JClass addRuntime(Class<?> clazz) {
        JClass g = generatedRuntime.get(clazz);
        if (g == null) {
            // put code into a separate package to avoid name conflicts.
            JPackage implPkg = getUsedPackages(Aspect.IMPLEMENTATION)[0].subPackage("runtime");
            g = generateStaticClass(clazz, implPkg);
            generatedRuntime.put(clazz, g);
        }
        return g;
    }

    public JClass generateStaticClass(Class<?> src, JPackage out) {
        JStaticJavaFile sjf = new JStaticJavaFile(out, getShortName(src), src, null);
        out.addResourceFile(sjf);
        return sjf.getJClass();
    }

    private String getShortName(Class<?> src) {
        String name = src.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

}
