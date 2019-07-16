package io.kurumi.ntt.fragment.idcard;

/**
 * 类名：AreaCode
 * 作者：Yun.Lei
 * 创建日期：2017-07-12 17:59
 */

public class AreaCode {


    /**
     * id : 3829
     * areaCode : 820000
     * province : 澳门特别行政区
     * city : null
     * district : null
     * detail : 澳门特别行政区
     */

    private int id;
    private String areaCode;
    private String province;
    private String city;
    private String district;
    private String detail;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
		
		public String getFull() {
				
				String full = getDetail();
				
				if (getCity() != null && full.startsWith(getProvince()) && !full.contains(getCity())) {
						
						full = getProvince() + getCity() + full.substring(getProvince().length());
						
				}
				
				return full;
				
		}
		
		
}
