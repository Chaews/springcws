package ezenwebcws.service;

import ezenwebcws.domain.board.BoardEntity;
import ezenwebcws.domain.board.BoardRepository;
import ezenwebcws.domain.board.CategoryEntity;
import ezenwebcws.domain.board.CategoryRepository;
import ezenwebcws.domain.member.MemberEntity;
import ezenwebcws.domain.member.MemberRepository;
import ezenwebcws.dto.BoardDto;
import ezenwebcws.dto.LoginDto;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BoardService {

    // DAO 호출 // Repository 호출
    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private HttpServletRequest request;

    // 1. C [ 인수 : 게시물 dto ]
    @Transactional
    public boolean save(BoardDto boardDto){
        // 1. 세션 호출
//        LoginDto loginDto = (LoginDto) request.getSession().getAttribute("login");

        // 1. 인증된 세션 호출
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 2. 인증 정보 가져오기
        Object principal = authentication.getPrincipal();
        // 3. 일반회원 : UserDetails  Oauth
        String mid = null;
        if(principal instanceof UserDetails){
            mid = ((UserDetails)principal).getUsername();
            System.out.println("일반 회원으로 글쓰기~~~~~" + principal.toString());

        }else if(principal instanceof DefaultOAuth2User){
            System.out.println("oauth2 회원 으로 글쓰기~~~~~" + principal.toString());
            // 회원정보 요청키를 이용한 구분 짓기
            Map<String,Object> map = ((DefaultOAuth2User) principal).getAttributes();
            if(map.get("response")!=null){ // 1. 네이버 일경우 [ Attributes에 response 이라는 키가 존재하면 ]
                Map<String, Object> map2 = ( Map<String, Object>)map.get("response");
                mid = map2.get("email").toString().split("@")[0];
            }
            else{ // 2. 카카오 일경우 [ Attributes에 response 이라는 키가 존재하면 ]
                Map<String, Object> map2 = ( Map<String, Object>)map.get("kakao_account");
                mid = map2.get("email").toString().split("@")[0];
            }
        }
        else{
            return false;
        }

        if(mid != null){ // 로그인이 되어있으면
            // 2. 로그인된 회원의 엔티티 찾기
            Optional<MemberEntity> optionalMember = memberRepository.findBymid(mid);
                // findById(pk키) => 반환타입 : Optional클래스 [ Null값도받아준다 : NullPointerException 방지 ]
            if(optionalMember.isPresent()){ // null 아니면
                // 3. Dto -> entity
                // save(엔티티) => 반환타입 : 저장된 엔티티

                    // 만약에 기존에 있는 카테고리이면
                    boolean categorysw = false;
                    int cno = 0 ;
                    List<CategoryEntity> categoryEntities =  categoryRepository.findAll();
                    for(CategoryEntity entity : categoryEntities){
                        if(entity.getCname().equals(boardDto.getCategory())){
                            categorysw = true;
                            cno = entity.getCno();
                        }
                    }
                    CategoryEntity categoryEntity = null ;
                    if(!categorysw){
                        // 1. 카테고리 생성
                        categoryEntity = CategoryEntity.builder().cname(boardDto.getCategory()).build();
                        categoryRepository.save(categoryEntity);
                    }else{
                        categoryEntity = categoryRepository.findById(cno).get();
                    }


                BoardEntity boardEntity =  boardRepository.save(boardDto.toBoardEntity());
                // 4. 작성회원 엔티티 추가
                boardEntity.setMemberEntity(optionalMember.get());
                boardEntity.setCategoryEntity(categoryEntity);
                    // 회원엔티티에 게시물 연결
                    optionalMember.get().getBoardEntityList().add(boardEntity);
                    // 카테고리 엔티티에 게시물 연결
                    categoryEntity.getBoardEntityList().add(boardEntity);

                return true;
            }
        } else { // 로그인이 안되어 있는경우
            return false;
        }
        return false;
    }

    // 2. R [ 인수 : 없음  / 반환 : 1. JSON(js 사용할경우 가능한한 JSON) 2. MAP ]
    public JSONObject getboardlist(int cno, String key, String keyword, int page){
        JSONObject object = new JSONObject();
        JSONArray jsonArray = new JSONArray();

        Page<BoardEntity> boardEntities = null ; // 선언만

        // Pageable : 페이지처리 관련 인터페이스
        // PageRequest : 페이징처리 관련 클래스
            // PageRequest.of(page,size) : 페이징처리 설정
            // page = "현재페이지" [0부터 시작]
            // size = "현재페이지에 보여줄 게시물 수"
            // sort = "정렬기준" [Sort.by(Sort.Direction.DESC, "정렬필드명")]
                // sort 문제점 : 정렬필드명에 _ 인식 불가능
        Pageable pageable = PageRequest.of(page-1,3 , Sort.by(Sort.Direction.DESC, "bno"));


        // 필드에 따른 검색 기능
        if(key.equals("btitle")){
            System.out.println("제목 검색");
            boardEntities = boardRepository.findBybtitle(cno, keyword, pageable);
        }
        else if(key.equals("bcontent")){
            System.out.println("내용 검색");
            boardEntities = boardRepository.findBybcontent(cno, keyword, pageable);
        }
        else if(key.equals("mid")){
            System.out.println("작성자 검색");
            // 입력받은 mid -> [mno] 엔티티 변환
//            Optional<MemberEntity> optionalMember = memberRepository.findBymid(keyword);
//            if(optionalMember.isPresent()){
//                MemberEntity memberEntity = optionalMember.get();
//                boardEntities = boardRepository.findBymno(cno, memberEntity, pageable);
//            }
//            else{
//                return object; // 검색 결과가 없으면
//            }
            Optional<MemberEntity> memberEntityList = memberRepository.findBymid(keyword);

            boardEntities = boardRepository.findBymno(cno, memberEntityList, pageable);

        }
        else{
            boardEntities = boardRepository.findBybtitle(cno, keyword, pageable);
        }

        // 페이지에 표시할 총 페이징 버튼 개수
        int btncount = 5;

        // 시작번호버튼의 번호 [ (현재페이지 / 표시할 버튼 수 ) * 표시할 버튼수 + 1 ]
        int startbtn = ((page-1)/btncount)*btncount + 1;

        // 끝번호버튼의 번호 [ 시작버튼 + 표시할 버튼 수 -1 ]
        int endbtn = startbtn+btncount-1;
        if(endbtn>boardEntities.getTotalPages()){ // 만약에 끝번호가 마지막 페이지보다 크면 끝번호는 마지막페이지번호로 사용
            endbtn = boardEntities.getTotalPages();
        }
        object.put("totalpage",boardEntities.getTotalPages()); // 전체 페이지 수
        object.put("startbtn",startbtn); // 시작버튼
        object.put("endbtn",endbtn); // 끝버튼

        // 엔티티 반환타입을 리스트가 아닌 페이지 인터페이스 할 경우에
        System.out.println("검색된 총 게시물 수 : " + boardEntities.getTotalElements());
        System.out.println("검색된 총 페이지 수 : " + boardEntities.getTotalPages());
        System.out.println("검색된 게시물 정보 : " + boardEntities.getContent());
        System.out.println("현재 페이지 수 : " + boardEntities.getNumber());
        System.out.println("현재 페이지의 게시물 수 : " + boardEntities.getNumberOfElements());
        System.out.println("현재 페이지가 첫페이지 여부 확인 : " + boardEntities.isFirst());
        System.out.println("현재 페이지가 마지막 페이지 여부 확인 : " + boardEntities.isLast());

        // * data : 모든 엔티티 -> json 변환
        for(BoardEntity entity : boardEntities){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("bno", entity.getBno());
            jsonObject.put("btitle", entity.getBtitle());
            jsonObject.put("bcontent", entity.getBcontent());
            jsonObject.put("bview", entity.getBview());
            jsonObject.put("blike", entity.getBlike());
            jsonObject.put("bindate", entity.getCreatedate().format(DateTimeFormatter.ofPattern("yy-MM-dd hh:mm:ss")));
            jsonObject.put("bmodate", entity.getModifiedate().format(DateTimeFormatter.ofPattern("yy-MM-dd hh:mm:ss")));
            jsonObject.put("mid", entity.getMemberEntity().getMid());
            jsonArray.put(jsonObject);
        }


        object.put("data",jsonArray); // 리스트를 추가
        // * 모든 엔티티 -> JSON 변환
        return object;
    }

    // 2. R : 개별조회 [ 게시물번호 ]
    @Transactional
    public JSONObject getboard(int bno){
        String ip = request.getRemoteAddr(); // 사용자의 ip 가져오기

        Optional<BoardEntity> optional = boardRepository.findById(bno);
        BoardEntity entity = optional.get();
        // ip와 bno 합쳐서 세션 부여
        Object ipbno = request.getSession().getAttribute(ip+bno);
        if(ipbno!=null){
            // 만약에 세션이 있으면
            System.out.println("있다");
        }
        else{ // 만약에 세션이 없으면
            System.out.println("없다");
            request.getSession().setAttribute(ip+bno,1);
            request.getSession().setMaxInactiveInterval(86400); // 세션 허용시간 [ 초단위 ]
            entity.setBview(entity.getBview()+1);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("bno",entity.getBno());
        jsonObject.put("btitle",entity.getBtitle());
        jsonObject.put("bcontent",entity.getBcontent());
        jsonObject.put("bview",entity.getBview());
        jsonObject.put("blike",entity.getBlike());
        jsonObject.put("bindate",entity.getCreatedate().format(DateTimeFormatter.ofPattern("yy-MM-dd hh:mm:ss")));
        jsonObject.put("bmodate",entity.getModifiedate().format(DateTimeFormatter.ofPattern("yy-MM-dd hh:mm:ss")));
        jsonObject.put("mid",entity.getMemberEntity().getMid());
        return jsonObject;
    }

    @Transactional
    // 3. U [ 인수 : 게시물 번호, 수정할 내용들 -> dto ]
    public boolean update(BoardDto boardDto){
        BoardEntity entity = boardRepository.findById(boardDto.getBno()).get();
        entity.setBtitle(boardDto.getBtitle());
        entity.setBcontent(boardDto.getBcontent());
        return true;
    }

    // 4. D [ 인수 : 삭제할 번호 ]
    @Transactional
    public boolean delete(int bno){
        BoardEntity boardEntity = boardRepository.findById(bno).get();
        boardRepository.delete(boardEntity);
        return true;
    }


    // 5. 카테고리 호출메소드
    public JSONArray getcategorylist(){
        List<CategoryEntity> categoryEntities = categoryRepository.findAll();
        JSONArray jsonArray = new JSONArray();
        for(CategoryEntity entity : categoryEntities){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("cno",entity.getCno());
            jsonObject.put("cname",entity.getCname());
            jsonArray.put(jsonObject);
        }
        return jsonArray;
    }
}