package com.deepdimen.matrix.zenflow.media;

/** Single byte range only. HTTP adapter maps invalid/unsatisfiable to 416 and bytes */
public record ZenflowMediaRange(long start,long end,long total,boolean partial) {
    public long length(){return end-start+1;}
    public String contentRange(){return "bytes "+start+"-"+end+"/"+total;}
    public static ZenflowMediaRange parse(String header,long total){
        if(total<1)throw invalid();
        if(header==null)return new ZenflowMediaRange(0,total-1,total,false);
        if(header.length()>100||!header.matches("bytes=[0-9]*-[0-9]*"))throw invalid();
        String[] parts=header.substring(6).split("-",-1);
        try{
            long start,end;
            if(parts[0].isEmpty()){
                long suffix=Long.parseLong(parts[1]);if(suffix<1)throw invalid();
                start=Math.max(0,total-suffix);end=total-1;
            }else{
                start=Long.parseLong(parts[0]);end=parts[1].isEmpty()?total-1:Math.min(total-1,Long.parseLong(parts[1]));
                if(start>=total||start>end)throw invalid();
            }
            return new ZenflowMediaRange(start,end,total,true);
        }catch(NumberFormatException error){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("MEDIA_RANGE_NOT_SATISFIABLE");}
}
