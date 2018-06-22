package pivotgraph;

public class StatBundle {

	//in 
	int onelinks;
	int twolinks;
	int triangles;	
	int inferred;
	int conflicts;
	int plus;
	int wasted;
	int total;
	
	public StatBundle() {
		onelinks = 0;
		twolinks = 0;
		triangles = 0;
		conflicts = 0;
		total = 0;
		plus = 0;
		inferred = 0;
		wasted = 0;
	}
	
	public void addStat(String type) {
		total++;
		if(type.equals("one")) onelinks++;
		else if(type.equals("two")) twolinks++;
		else if(type.equals("tri")) triangles++;
		else if(type.equals("con")) conflicts++;
		else if(type.equals("plu")) plus++;
		else if(type.equals("inf")) inferred++;
		else if(type.equals("was")) wasted++;
		else {
			System.out.println("Case untreated : "+type);
		}
	}
	
	@Override
	public String toString() {
		if(total==0) return "	No entries\n";
		String ret = "	Total links :                            "+total+"\n";
		ret += "	Single links :                             "+onelinks+"/"+total+", "+((float)onelinks/(float)total)*100+"%\n";
		ret += "	Links belonging to double-link relations : "+twolinks+"/"+total+", "+((float)twolinks/(float)total)*100+"%\n";
		ret += "	Links belonging to triangles :             "+triangles+"/"+total+", "+((float)triangles/(float)total)*100+"%\n";
		ret += "	Links belonging to conflicting relations : "+conflicts+"/"+total+", "+((float)conflicts/(float)total)*100+"%\n";
		ret += "	Links belonging to multiple relations :    "+plus+"/"+total+", "+((float)plus/(float)total)*100+"%\n";
		ret += "	Inferred links :                           "+inferred+"/"+total+", "+((float)inferred/(float)total)*100+"%\n";
		ret += "	Wasted links :                             "+wasted+"/"+total+", "+((float)wasted/(float)total)*100+"%\n";
		return ret;
	}
}
