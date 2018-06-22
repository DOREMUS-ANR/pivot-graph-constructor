package pivotgraph;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.Cell;

import fr.inrialpes.exmo.align.parser.AlignmentParser;
import sameAs.Link;
import sameAs.LinkList;

public class prepCoda {

	public static void main(String[] args) throws AlignmentException, IOException {
		
		int nbSure = 0;
		int nbInvalid = 0;
		int nbInferred = 0;
		float threshold;
		if(args.length==0) {
			System.err.println("No threshold input. Setting default value : 1.0");
			threshold = 1.0f;
		} else {
			threshold = Float.parseFloat(args[0]);
		}
		ArrayList<Link> treated = new ArrayList<>();	//treated links
		ArrayList<Link> written = new ArrayList<>();	//written links
		System.out.println("Threshold = "+threshold);
		
		System.out.println("Links loading ...");
		AlignmentParser aparser = new AlignmentParser(0);
		Alignment results = aparser.parse(new File("store/finalResults.rdf").toURI());
		List<Cell> list = Collections.list(results.getElements());
		LinkList linkList = new LinkList();
		for (Cell cell : list) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList.add(link);
		}
		System.out.println("Total links loaded : "+linkList.size());
				
		//file for triangles and inferred links
		FileWriter alignFile= new FileWriter ("store/surelinks.rdf");
		BufferedWriter bw = new BufferedWriter (alignFile);
		PrintWriter pw = new PrintWriter (bw);
		pw.println("<?xml version='1.0' encoding='utf-8'?>");
		pw.println("<rdf:RDF xmlns='http://knowledgeweb.semanticweb.org/heterogeneity/alignment'");
		pw.println("xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'");
		pw.println("xmlns:xsd='http://www.w3.org/2001/XMLSchema#'>");
		pw.println("<Alignment>");
		pw.println("<xml>yes</xml>");
		pw.println("<level>0</level>");
		pw.println("<type>??</type>");

		//file for coda (all other links that need validation)
		FileWriter codafile = new FileWriter ("store/tovalidate.rdf");
		BufferedWriter bw_coda = new BufferedWriter (codafile);
		PrintWriter pw_coda = new PrintWriter (bw_coda);
		pw_coda.println("<?xml version='1.0' encoding='utf-8'?>");
		pw_coda.println("<rdf:RDF xmlns='http://knowledgeweb.semanticweb.org/heterogeneity/alignment'");
		pw_coda.println("xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'");
		pw_coda.println("xmlns:xsd='http://www.w3.org/2001/XMLSchema#'>");
		pw_coda.println("<Alignment>");
		pw_coda.println("<xml>yes</xml>");
		pw_coda.println("<level>0</level>");
		pw_coda.println("<type>??</type>");
		
		int cpt = 0;	//tours de boucles
		int treat = 0;	//liens traités
		/*
		 * we iterate on all the links
		 * from a link, a have a linked to b, a-b
		 * we get all the links going from something to a and from b to something
		 * if both links exist and are the same, it is a triangle ( a-b-c-a )
		 * if there is two links but no third, first verify that it is not a conflict
		 *   if not a conflict, we try to infer a third link if the conf score is high enough
		 *   if no conf scores are too low, we pass them on to coda as two-links
		 * any other case, we just pass it on to coda
		 */
		for(int i=0; i<linkList.size(); i++) {
			cpt++;
			Link curr = linkList.get(i);
			if(!treated.contains(curr)) {
				treat++;
				treated.add(curr);
				List<String> linkTo = linkList.getObj2OfURI1(curr.getObj2());	//link from curr to something
				List<String> linkFrom = linkList.getObj1OfURI2(curr.getObj1());	//link from something to curr
				for(String s : linkTo) {
					if(!treated.contains(getLinkFromUris(linkList, curr.getObj2(),s)))
						treated.add(getLinkFromUris(linkList, curr.getObj2(),s));
				}
				for(String s : linkFrom) {
					if(!treated.contains(getLinkFromUris(linkList, s, curr.getObj1())))
						treated.add(getLinkFromUris(linkList, s, curr.getObj1()));
				}
				if(linkTo.size()==1 && linkFrom.size()==1) {
					Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
					Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
					if(linkTo.get(0).equals(linkFrom.get(0))) {
						//triangle, everything is sure
						nbSure = nbSure+writeLink(pw, curr, written);
						nbSure = nbSure+writeLink(pw, l_linkto, written);
						nbSure = nbSure+writeLink(pw, l_linkfrom, written);
					} else { 
						//conflict c-c'
						//in a conflict, everything but the absolutely sure links (conf==1) must be validated manually
						if(curr.getScore()==1) 
							nbSure = nbSure+writeLink(pw, curr, written);	
						else
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						if(l_linkto.getScore()==1) 
							nbSure = nbSure+writeLink(pw, l_linkto, written);
						else 
							nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
						if(l_linkfrom.getScore()==1)
							nbSure = nbSure+writeLink(pw, l_linkfrom, written);
						else 
							nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
					}
				} else if((linkTo.size()==1 && linkFrom.isEmpty()) || (linkTo.isEmpty() && linkFrom.size() == 1)) { 			
					if(linkFrom.isEmpty()) {
						//there is no link going to our resource a from c, but maybe there is a conflict
						//meaning there might be a link going from the c we just found to another a
						List<String> linkCtoA = linkList.getObj2OfURI1(linkTo.get(0)); 
						for(String s : linkCtoA) {
							if(!treated.contains(getLinkFromUris(linkList, linkTo.get(0), s))) 
								treated.add(getLinkFromUris(linkList, linkTo.get(0),s));	
						}
						if(linkCtoA.isEmpty()) {
							//no conflict
							if(getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)).getScore() >= threshold && curr.getScore()>= threshold) {
								//inferrence
								nbInferred++;
								nbSure = nbSure+writeLink(pw, curr, written);
								nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
								writeLink(pw, linkTo.get(0), curr.getObj1());
							} else {
								//two links
								//if conf score is superior or equal to threshold we consider them sure
								if(curr.getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, curr, written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
								if(getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)).getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
								else 
									nbInvalid=nbInvalid+writeLink(pw_coda, getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0)), written);
							}
						} else if(linkCtoA.size()==1){
							//conflict a-a'
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
							Link l_linkCtoA = getLinkFromUris(linkList, linkTo.get(0), linkCtoA.get(0));
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
							if(l_linkto.getScore()==1)
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							if(l_linkCtoA.getScore()==1)
								nbSure = nbSure+writeLink(pw, l_linkCtoA, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoA, written);
							
						} else {
							//multiple links problems
							//write current link
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else 
								nbInvalid+=nbInvalid+writeLink(pw_coda, curr, written); 
							
							//write link from b to c
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), linkTo.get(0));
							if(l_linkto.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							
							//write links from c to other a's
							for(String s : linkCtoA) {
								Link l_linkCtoA = getLinkFromUris(linkList, linkTo.get(0), s);
								if(l_linkCtoA.getScore()==1)
									nbSure = nbSure+writeLink(pw, l_linkCtoA, written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoA, written);
							}
						}
					} else {	//linkTo.isEmpty()
						//there is no link going from our resource b to c, but maybe there is a conflict
						//meaning there might be a link going to the c we just found from another b
						List<String> linkCtoB = linkList.getObj1OfURI2(linkFrom.get(0)); 
						for(String s : linkCtoB) {
							if(!treated.contains(getLinkFromUris(linkList, s, linkFrom.get(0))))
								treated.add(getLinkFromUris(linkList, s, linkFrom.get(0)));
						}
						if(linkCtoB.isEmpty()) {
							//no conflict
							if(getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()).getScore() >= threshold && curr.getScore()>=threshold) {
								//inferrence
								nbInferred++;
								nbSure = nbSure+writeLink(pw, curr, written);
								nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								writeLink(pw, curr.getObj2(), linkFrom.get(0));
							} else {
								//two links
								if(curr.getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, curr, written);	
								else 
									nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
								if(getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()).getScore()>=threshold) 
									nbSure = nbSure+writeLink(pw, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								else
									nbInvalid=nbInvalid+writeLink(pw_coda, getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1()), written);
								
							}
						} else if (linkCtoB.size()==1){
							//conflict b-b'
							Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
							Link l_linkCtoB = getLinkFromUris(linkList, linkCtoB.get(0), linkFrom.get(0));
							if(curr.getScore()==1)
								nbSure = nbSure+writeLink(pw, curr, written);
							else
								nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
							if(l_linkfrom.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							if(l_linkCtoB.getScore()==1) 
								nbSure = nbSure+writeLink(pw, l_linkCtoB, written);
							else 
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoB, written);
						} else {
							//linked to multiple links. to validate.
							if(curr.getScore()==1) {
								nbSure = nbSure+writeLink(pw, curr, written);
							} else {
								nbInvalid++;
								writeLink(pw_coda, curr, written);
							}
							Link l_linkfrom = getLinkFromUris(linkList, linkFrom.get(0), curr.getObj1());
							if(l_linkfrom.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							}
							for(String s : linkCtoB) {
								Link l_linkCtoB = getLinkFromUris(linkList, s, linkFrom.get(0));
								if(l_linkCtoB.getScore()==1) {
									nbSure = nbSure+writeLink(pw, l_linkCtoB, written);
								} else {
									nbInvalid=nbInvalid+writeLink(pw_coda, l_linkCtoB, written);
								}
							}
						}
					}
				} else {// single links or multiples links
					if(linkList.getNbEquivalences(curr.getObj1())==1 && linkList.getNbEquivalences(curr.getObj2())==1) {
						//single link 
						if(curr.getScore()>=threshold) {
							nbSure = nbSure+writeLink(pw, curr, written);
						} else {
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						}
					} else {
						//uncharted territory
						//multiple links, rare, large and complex multistructures
						//here be dragons
						//we only write them in sure if they are absolutely secure (conf==1)
						if(curr.getScore()==1) {
							nbSure = nbSure+writeLink(pw, curr, written);
						} else {
							nbInvalid=nbInvalid+writeLink(pw_coda, curr, written);
						}
						for(String s : linkTo) {
							Link l_linkto = getLinkFromUris(linkList, curr.getObj2(), s);
							if(l_linkto.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkto, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkto, written);
							}
						}
						for(String s : linkFrom) {
							Link l_linkfrom = getLinkFromUris(linkList, s, curr.getObj1());
							if(l_linkfrom.getScore()==1) {
								nbSure = nbSure+writeLink(pw, l_linkfrom, written);
							} else {
								nbInvalid=nbInvalid+writeLink(pw_coda, l_linkfrom, written);
							}
						}
					}
				}
			}
		}
		
		pw.println("</Alignment>");
		pw.println("</rdf:RDF>");
		pw.close();
		bw.close();
		alignFile.close();
		
		pw_coda.println("</Alignment>");
		pw_coda.println("</rdf:RDF>");
		pw_coda.close();
		bw_coda.close();
		codafile.close();
		
		Set<Link> set = new HashSet<Link>(treated);
		if(set.size()<treated.size()) System.out.println(treated.size()-set.size()+" duplicates in treated");
		
		
		System.out.println("Tours de boucles : "+cpt);
		System.out.println("Liens traités   : "+treat);
		System.out.println("Sure links      : "+nbSure);
		System.out.println("To Validate     : "+nbInvalid);
		System.out.println("Treated size : "+treated.size());
		System.out.println("Written size : "+written.size());
		System.out.println("Total Written Links  : "+(written.size()+nbInferred)+"  ("+cpt+" original links, "+nbInferred+" inferred links)");
		
	}
	
	/*
	 * writes a link with pw if it is not written already
	 */
	public static int writeLink(PrintWriter pw, Link l, List<Link> written) {
		if(!written.contains(l)) {
			pw.println("<map>");
			pw.println("<Cell>");
			pw.println("<entity1 rdf:resource=\""+l.getObj1()+"\"/>");
			pw.println("<entity2 rdf:resource=\""+l.getObj2()+"\"/>");
			pw.println("<measure rdf:datatype=\"http://www.w3.org/2001/XMLSchema#float\">"+l.getScore()+"</measure>");
			pw.println("<relation> = </relation>");
			pw.println("</Cell>");
			pw.println("</map>");
			pw.println("");
			written.add(l);
			return 1;
		} else return 0;
	}
	
	/*
	 * used to write links that are not present in finalResults
	 * e.g. to write links deduced from inferrences
	 */
	public static void writeLink(PrintWriter pw, String uri1, String uri2) {	
		pw.println("<map>");
		pw.println("<Cell>");
		pw.println("<entity1 rdf:resource=\""+uri1+"\"/>");
		pw.println("<entity2 rdf:resource=\""+uri2+"\"/>");
		pw.println("<measure rdf:datatype=\"http://www.w3.org/2001/XMLSchema#float\">"+1.0+"</measure>");
		pw.println("<relation> = </relation>");
		pw.println("</Cell>");
		pw.println("</map>");
		pw.println("");
	}
	
	public static Link getLinkFromUris(LinkList l, String uri1, String uri2) {
		int i = l.getLinkID(uri1, uri2);
		if(i>=0) {
			Link lin = l.get(i);
			return lin;
		} else return null;
	}
	
	/*
	 * used to handle all multiple links from a link
	 * e.g. if a resource is linked to at least two resources from the same base
	 * for each link we discover that is compromised (e.g. linked to multiple links), we call this function
	 * return true if there were any multiple links discovered processing this link, so we do not treat it afterwards
	 */
	/*public static int handleMultipleLinks(Link l, List<Link> treated, LinkList linkList, PrintWriter pw_coda) {
		int nbLinksObj1 = linkList.getNbEquivalences(l.getObj1());
		int nbLinksObj2 = linkList.getNbEquivalences(l.getObj2());
		int nbMul = 0;
		if((nbLinksObj1>2) || (nbLinksObj2>2)) {
			
			for(String link : linkList.getObj2OfURI1(l.getObj1())) {
				//multiples from a to b
				Link curr = getLinkFromUris(linkList, l.getObj1(), link);
				if(!treated.contains(curr)) {
					treated.add(curr);
					writeLink(pw_coda, curr);
					nbMul++;
					nbMul = nbMul + handleMultipleLinks(curr, treated, linkList, pw_coda);
				}
			}
			for(String link : linkList.getObj1OfURI2(l.getObj2())) {
				//multiples from b to a
				Link curr = getLinkFromUris(linkList, link, l.getObj2());
				if(!treated.contains(curr)) {
					treated.add(curr);
					writeLink(pw_coda, curr);
					nbMul++;
					nbMul = nbMul + handleMultipleLinks(curr, treated, linkList, pw_coda);
				}
			}
		}
		return (nbMul);
		
	}*/

	
}
