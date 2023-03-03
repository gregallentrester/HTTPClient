package net.greg.examples.httpclient.utils;

import java.io.StringReader;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import lombok.extern.slf4j.Slf4j;

import net.greg.examples.httpclient.pal.schemamap.EmployeeList;


@Slf4j
public class PalXMLToObject {

  public EmployeeList unmarshall(final String palXml) {

    var employeeList =
      new EmployeeList();

    try {
      // var?
      var jaxbContext =
        JAXBContext.newInstance(EmployeeList.class);

      // var?
      var jaxUnmarshaller =
        jaxbContext.createUnmarshaller();

      employeeList = (EmployeeList)
        jaxUnmarshaller.unmarshal(
          new StringReader(palXml));
    }
    catch (JAXBException e) {
      log.error(
        "Error in unmarshalXML is " + e.toString());
    }

    return employeeList;
  }
}
