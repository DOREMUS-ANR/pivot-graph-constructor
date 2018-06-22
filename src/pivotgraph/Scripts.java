package pivotgraph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.Cell;

import fr.inrialpes.exmo.align.parser.AlignmentParser;
import sameAs.Link;
import sameAs.LinkList;

public class Scripts {

	public static void main(String[] args) throws IOException, AlignmentException {
		//checkResults();
		//searchResource("http://data.doremus.org/expression/33d88da9-b90b-3e06-a997-fac26b9bb280");
		//searchDoubles();
		searchMissing();
	}
	
	public static void extractResources(ArrayList<String> resources) throws IOException {
		//extract a list of resources from their base, wherever it is, and writes it in a file
		FileReader f_bnf = new FileReader("store/bnf.ttl");
		FileReader f_rf = new FileReader("store/rf.ttl");
		FileReader f_pp = new FileReader("store/pp.ttl");
		BufferedReader b_bnf = new BufferedReader(f_bnf);
		BufferedReader b_rf = new BufferedReader(f_rf);
		BufferedReader b_pp = new BufferedReader(f_pp);
		ArrayList<String> content = new ArrayList<>();
		boolean found = false;
		boolean written = false;
		String sCurrentLine = null;
		FileWriter fw = new FileWriter("store/results.txt");
		BufferedWriter bw = new BufferedWriter(fw);
		

		
		for(String s : resources) {
			while (((sCurrentLine = b_pp.readLine()) != null) && !written) {	//pp check
				content.add(sCurrentLine);
				if(sCurrentLine.startsWith("<http")) {
					if(sCurrentLine.contains(s)) {
						found = true;
					}
				} else if(sCurrentLine.endsWith(".")) {
					if(found) {
						written = true;
						bw.write("-- Origin : PP\n");
						for(String si : content) {
							bw.write(si);
							bw.write("\n");
						}
						bw.write("\n");
					}
					content.clear();
				}
			}	
			f_pp = new FileReader("store/pp.ttl");
			b_pp = new BufferedReader(f_pp);
			sCurrentLine = null;
			while (((sCurrentLine = b_rf.readLine()) != null) && !written) {	//rf check
				content.add(sCurrentLine);
				if(sCurrentLine.startsWith("<http")) {
					if(sCurrentLine.contains(s))
						found = true;
				} else if(sCurrentLine.endsWith(".")) {
					if(found) {
						written = true;
						bw.write("-- Origin : RF\n");
						for(String si : content) {
							bw.write(si);
							bw.write("\n");
						}
						bw.write("\n");
					}
					content.clear();
				}
			}
			f_rf = new FileReader("store/rf.ttl");
			b_rf = new BufferedReader(f_rf);
			while (((sCurrentLine = b_bnf.readLine()) != null) && !written) {	//bnf check
				content.add(sCurrentLine);
				if(sCurrentLine.startsWith("<http")) {
					if(sCurrentLine.contains(s))
						found = true;
				} else if(sCurrentLine.endsWith(".")) {
					if(found) {
						written = true;
						bw.write("-- Origin : BNF\n");
						for(String si : content) {
							bw.write(si);
							bw.write("\n");
						}
						bw.write("\n");
					}
					content.clear();
				}
			}
			f_bnf = new FileReader("store/bnf.ttl");
			b_bnf = new BufferedReader(f_bnf);
			sCurrentLine = null;
			if(!written) bw.write("RESOURCE "+s+" NOT FOUND\n\n");
			written = false;
			found = false;
		}
		
		b_bnf.close();
		b_rf.close();
		b_pp.close();
		bw.close();
		f_bnf.close();
		f_rf.close();
		f_pp.close();
		fw.close();

	}
	
	/*
	 * searches every resource written at least two times in finalResults
	 */
	public static void searchDoubles() throws IOException, AlignmentException {
	
		ArrayList<Link> doublesResource = new ArrayList<>();
		int nbDoubles = 0;
		AlignmentParser aparser = new AlignmentParser(0);
		Alignment results = aparser.parse(new File("store/tovalidate.rdf").toURI());
		List<Cell> list = Collections.list(results.getElements());
		LinkList linkList = new LinkList();
		for (Cell cell : list) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList.add(link);
		}

		AlignmentParser aparser2 = new AlignmentParser(0);
		Alignment results2 = aparser.parse(new File("store/surelinks.rdf").toURI());
		List<Cell> list2 = Collections.list(results2.getElements());
		LinkList linkList2 = new LinkList();
		for (Cell cell : list2) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList2.add(link);
		}
		
		for(int i=0; i<linkList.size(); i++) {
			Link l1 = linkList.get(i);
			for(int j=0; j<linkList2.size(); j++) {
				Link l2 = linkList2.get(j);
				if(l1.equals(l2)) {
					if(!doublesResource.contains(l1)) doublesResource.add(l1);
					nbDoubles++;
				}
			}
		}

		for(int i=0; i< doublesResource.size(); i++) {
			System.out.println(doublesResource.get(i).getObj1());
			System.out.println(doublesResource.get(i).getObj2());
			System.out.println();
		}
		System.out.println(nbDoubles+" doubles");
		System.out.println(doublesResource.size()+" unique duplicated links");
		System.out.println(linkList.size());
		
	}
	
	public static void searchResource(String s) throws IOException{
		BufferedReader br = null;
		FileReader fr = null;
		fr = new FileReader("store/rf.ttl");
		br = new BufferedReader(fr);
		String sCurrentLine;
		
		int line = 0;
		int cpt = 0;
		while ((sCurrentLine = br.readLine()) != null) {
			line++;
			if(sCurrentLine.contains(s)) {
				System.out.println("l"+line+" : "+sCurrentLine);
				cpt++;
			} 
		}
		System.out.println("\n"+cpt+" occurences");
		
		br.close();
		fr.close();

	}
	
	public static void checkFromResults() throws IOException {
		BufferedReader br = null;
		FileReader fr = null;
		fr = new FileReader("store/finalResults.rdf");
		br = new BufferedReader(fr);
		ArrayList<String> results = new ArrayList<>();
		String sCurrentLine = null;
		while ((sCurrentLine = br.readLine()) != null) {
			if(sCurrentLine.contains("resource")) {
				String[] temp = sCurrentLine.split("\"");
				results.add(temp[1]);
			}
		}
		
		fr = new FileReader("store/plusgraph.rdf");
		br = new BufferedReader(fr);
		ArrayList<String> absents = new ArrayList<>();
		while((sCurrentLine = br.readLine()) != null) {
			if(sCurrentLine.contains("resource")) {
				String[] temp = sCurrentLine.split("\"");
				if(!contains(results, temp[1])) {
					//System.out.println(temp[1]);
					//System.out.println("------> "+results.get(0));
					absents.add(temp[1]);
				}
			}
		}
		
		fr.close();
		br.close();
		
		for(String s : absents) {
			System.out.println(s);
		}
		System.out.println(absents.size());
		
	}
	
	public static boolean contains(ArrayList<String> tab, String s) {
		boolean ret = false;
		for(String so : tab) {
			if(so.equals(s)) {
				ret = true;
			}
		}
		return ret;
	}
	
	public static void checkResults() throws IOException {
		BufferedReader br = null;
		FileReader fr = null;
		fr = new FileReader("store/pivotgraph.rdf");
		br = new BufferedReader(fr);

		int tri = 0;
		int uni = 0;
		int sin = 0;
		int des = 0;
		int resCount = 0;
		String sCurrentLine;
		
		while ((sCurrentLine = br.readLine()) != null) {
			if(sCurrentLine.contains("/rdf:D")) {
				if(resCount == 1) uni++;
				if(resCount == 2) sin++;
				if(resCount == 3) tri++;
				if(resCount > 3) des = des+resCount-1;
				resCount=0;
			} else if (sCurrentLine.contains("rdf:resource")) resCount++;
			//System.out.println(resCount);
		}
		
		System.out.println("Unlinked Resources : "+uni);
		System.out.println("Single links : "+sin);
		System.out.println("Triple links : "+tri);
		System.out.println("Multiple resources : "+des);
		System.out.println("Total : "+(sin+(tri*3)+des));
		
		/*fr = new FileReader("store/plusgraph.rdf");
		br = new BufferedReader(fr);
		int des = 0;
		resCount = 0;
		while ((sCurrentLine = br.readLine()) != null) {
			if(sCurrentLine.contains("rdf:resource")) resCount++;
			//else if (sCurrentLine.contains("/rdf:D")) des++;
		}
		System.out.println("Multiple links written : "+resCount);*/
		
		br.close();
		fr.close();
	}
	
	public static void searchMissing() throws AlignmentException, IOException {
		AlignmentParser aparser = new AlignmentParser(0);
		Alignment results = aparser.parse(new File("store/tovalidate.rdf").toURI());
		List<Cell> list = Collections.list(results.getElements());
		LinkList linkList = new LinkList();
		for (Cell cell : list) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList.add(link);
		}

		Alignment results2 = aparser.parse(new File("store/surelinks.rdf").toURI());
		List<Cell> list2 = Collections.list(results2.getElements());
		for (Cell cell : list2) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList.add(link);
		}
		
		Alignment results3 = aparser.parse(new File("store/finalResults.rdf").toURI());
		List<Cell> list3 = Collections.list(results3.getElements());
		LinkList linkList3 = new LinkList();
		for (Cell cell : list3) {
			Link link = new Link(cell.getObject1().toString(), cell.getObject2().toString(), cell.getStrength());
			linkList3.add(link);
		}

		ArrayList<Link> singles = new ArrayList<>();
		for(int i=0; i<linkList3.size(); i++) {
			boolean found = false;
			int j=0;
			while(j<linkList.size() && !found) {
				if(linkList.get(j).equals(linkList3.get(i))) {
					found = true;
				}
				j++;
			}
			if(!found) singles.add(linkList3.get(i));
		}
		
		for(Link l : singles) {
			System.out.println(l.getObj1());
			System.out.println(l.getObj2());
			System.out.println();
		}
		System.out.println(singles.size());
	}
	
	public void splitBNF() throws IOException {
		BufferedReader br = null;
		BufferedWriter bw = null;
		FileReader fr = null;
		FileWriter fw = null;
		fr = new FileReader("store/bnf.ttl");
		br = new BufferedReader(fr);
		fw = new FileWriter("store/bnf_f22only.ttl");
		bw = new BufferedWriter(fw);

		int nbF22 = 0;
		String sCurrentLine;

		boolean isF22 = false;
		ArrayList<String> object = new ArrayList<>();
		
		while ((sCurrentLine = br.readLine()) != null) {
			if(sCurrentLine.startsWith("@")) bw.write(sCurrentLine);
			if(sCurrentLine.contains("F22")) isF22 = true;
			
			if(sCurrentLine.endsWith(" .")) {
				object.add(sCurrentLine);
				if(isF22) {
					nbF22++;
					for(String s : object) {
						bw.write(s);
					}
				}
				object.clear();
				isF22 = false;
			} else {
				object.add(sCurrentLine);
			}
			
		}

		System.out.println("nbF22 : "+ nbF22);
		
		bw.close();
		br.close();
		fr.close();
		fw.close();		
	}
	

}
