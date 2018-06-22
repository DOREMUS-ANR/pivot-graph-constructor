package pivotgraph;

import org.apache.http.client.utils.URIBuilder;

import javax.xml.bind.DatatypeConverter;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.UUID;

public class ConstructURI {
	
  private static URIBuilder builder = new URIBuilder().setScheme("http").setHost("data.doremus.org");

  /*****************************************
   * The resource has no equiavent resources. 
   *****************************************/
  public static URI build(String db, String identifier, String className) throws URISyntaxException {
    String seed = "pivot"+ db + identifier + className;
    return builder.setPath("/" + getCollectionName(className) + "/" + generateUUID(seed)).build();
  }
  
  /*****************************************
   * Two equiavent resources. 
   *****************************************/
  public static URI build(String db1, String identifier1, String db2, String identifier2, String className) throws URISyntaxException {
    String seed = "pivot"+ db1 + identifier1 + db2 + identifier2+ className;
    return builder.setPath("/" + getCollectionName(className) + "/" + generateUUID(seed)).build();
  }
  
  /*****************************************
   * Three equiavent resources. 
   *****************************************/
  public static URI build(String db1, String identifier1, String db2, String identifier2, String db3, String identifier3, String className) throws URISyntaxException {
	  String seed = "pivot"+ db1 + identifier1 + db2 + identifier2+ db3 + identifier3 + className;
    return builder.setPath("/" + getCollectionName(className) + "/" + generateUUID(seed)).build();
  }

  private static String generateUUID(String seed) {
    // source: https://gist.github.com/giusepperizzo/630d32cc473069497ac1
    try {
      String hash = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-1").digest(seed.getBytes("UTF-8")));
      UUID uuid = UUID.nameUUIDFromBytes(hash.getBytes());
      return uuid.toString();
    } catch (Exception e) {
      System.err.println("[ConstructURI.java]" + e.getLocalizedMessage());
      return "";
    }
  }

  private static String getCollectionName(String className) {
    switch (className) {
      case "F22_SelfContainedExpression":
      case "F25_PerformancePlan":
      case "M43_PerformedExpression":
        return "expression";
      case "F28_ExpressionCreation":
      case "F30_PublicationEvent":
      case "M45_DescriptiveExpressionAssignment":
      case "F42_RepresentativeExpressionAssignment":
        return "event";
      case "F14_IndividualWork":
      case "F15_ComplexWork":
      case "F19_PublicationWork":
      case "M44_PerformedWork":
        return "work";
      case "F24_PublicationExpression":
        return "publication";
      case "F31_Performance":
      case "M42_PerformedExpressionCreation":
        return "performance";
      case "E21_Person":
        return "artist";
      case "E4_Period":
        return "period";
      case "prov":
        return "activity";
      default:
        throw new RuntimeException("[ConstructURI.java] Class not assigned to a collection: " + className);
    }
  }
}
