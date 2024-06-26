package com.ruoyi.system.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.service.ISysRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.entity.SysUser;//by jinx 20240513
import com.ruoyi.system.service.ISysUserService;//by jinx 20240513
import com.ruoyi.system.domain.SysUserRemark;
import com.ruoyi.system.service.ISysUserRemarkService;
import java.text.DecimalFormat;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.SysRemark;
import com.ruoyi.system.service.ISysRemarkService;
import com.ruoyi.common.utils.poi.ExcelUtil;

import com.ruoyi.system.domain.SysNotice;//by jinx 20240514.2
import com.ruoyi.system.service.ISysNoticeService;//by jinx 20240514.2

/**
 * 评论Controller
 *
 * @author ruoyi
 * @date 2024-05-11
 */
@RestController
@RequestMapping("/system/remark")
public class SysRemarkController extends BaseController
{
    @Autowired
    private ISysRemarkService sysRemarkService;
    @Autowired
    private ISysUserRemarkService sysUserRemarkService;
    @Autowired
    private ISysRoleService sysRoleService;
    @Autowired
    private ISysUserService userService;
    @Autowired
    private ISysNoticeService noticeService;
    /**
     * 查询评论列表
     */
    @PreAuthorize("@ss.hasPermi('system:remark:list')")
    @GetMapping("/list")
    public AjaxResult list(SysRemark sysRemark)
    {
        List<SysRemark> list = sysRemarkService.selectSysRemarkList(sysRemark);
        return success(list);
    }

    /**
     * 导出评论列表
     */
    @PreAuthorize("@ss.hasPermi('system:remark:export')")
    @Log(title = "评论", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysRemark sysRemark)
    {
        List<SysRemark> list = sysRemarkService.selectSysRemarkList(sysRemark);
        ExcelUtil<SysRemark> util = new ExcelUtil<SysRemark>(SysRemark.class);
        util.exportExcel(response, list, "评论数据");
    }

    /**
     * 获取评论详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:remark:query')")
    @GetMapping(value = "/{remarkId}")
    public AjaxResult getInfo(@PathVariable("remarkId") Long remarkId)
    {
        return success(sysRemarkService.selectSysRemarkByRemarkId(remarkId));
    }

    /**
     * 新增评论
     */
    @PreAuthorize("@ss.hasPermi('system:remark:add')")
    @Log(title = "评论", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysRemark sysRemark)
    {

        // 检查评论内容是否包含不当关键词
        if (containsProhibitedWords(sysRemark.getRemarkContent())) {
            return AjaxResult.error("评论内容包含不当关键词，无法添加评论");
        }
        long id=getUserId();

        String str = Long.toString(id);
        Long userId = getUserId();
        Long roleId = sysRoleService.selectRoleListByUserId(userId).get(0);
        String str2 =Long.toString(roleId);
        sysRemark.setReservedPort3(str2);
        sysRemark.setCreateBy(str);
        sysRemark.setStat(1L);


        if (sysRemark.getParentId() != null) {

            SysRemark parentRemark = sysRemarkService.selectSysRemarkByRemarkId(sysRemark.getParentId());
            if (parentRemark != null) {

                long stati=(parentRemark.getStat()+1L);

                sysRemark.setStat(stati > 4 ? 4 : stati);

                // 获取父级评论名称
                if(stati==3) {
                    String parentRemarkName = parentRemark.getRemarkName();
                    // 顺延父级评论名称作为当前评论名称

                    sysRemark.setRemarkName(parentRemarkName);

                }
                if(stati==2){

                    // 获取父级评论名称
                    String parentRemarkName = parentRemark.getRemarkName();
                    // 顺延父级评论名称作为当前评论名称
                    sysRemark.setRemarkName(parentRemarkName + " - " + sysRemark.getRemarkName());
                }
                if(stati>=3){
                    // 检查是否存在符合条件的评论项 by jinx start 20240513
                    SysRemark tmpt = new SysRemark();
                    tmpt.setParentId(sysRemark.getParentId());
                    tmpt.setCreateBy(str);
                    int existence = sysRemarkService.checkSysRemarkExistence(tmpt);
                    if (existence == 1 && roleId !=1 ) {
                        // 如果已存在符合条件的评论项，则拒绝添加评论
                        return AjaxResult.error("您已经添加对该课程的评价，无法再次添加评论");
                    }
                    //by jinx end 20240513

                    //增加用户名 by jinx start 20240513
                    SysUser sysuser= userService.selectUserById(id);
                    String name =sysuser.getNickName();
                    sysRemark.setRemarkContent(name+":"+sysRemark.getRemarkContent());
                    //by jinx end 20240513
                    if (sysRemark.getLikeCnt() == null) {
                        sysRemark.setLikeCnt(0L);
                    }


                    if (sysRemark.getReportCnt() == null) {
                        sysRemark.setReportCnt(0L);
                    }
                }
            }
        }
        // by firefly 20240514
        int res = sysRemarkService.insertSysRemark(sysRemark);
        // 因为求avg不会修改数据库，但是求avg需要先把add结果写数据库，所以使用此方法直接存res的状态
        // 如果当前评论的状态为3，则表示是第三级表项，需要更新其父级评论的RemarkContent为平均分数
        Long stati = sysRemark.getStat();
        System.out.printf("stat is %d\n", stati);
        if (stati != null && stati == 3) {
            // 获取当前评论的parentId
            Long parentId = sysRemark.getParentId();
            if (parentId != null) {
                // 调用获取平均值的方法，计算第二级表项的评论项的point均值
                Double avgPoint = sysRemarkService.selectAveragePoint(parentId);
                if (avgPoint != null) {
                    // 获取父级评论的信息
                    SysRemark parentRemark = sysRemarkService.selectSysRemarkByRemarkId(parentId);

                    // 构造更新后的RemarkContent字符串格式，保留父级评论的其他内容
                    String remarkContent = String.format("评分：%.2f", avgPoint);

                    // 更新父级评论的RemarkContent为更新后的内容
                    parentRemark.setRemarkContent(remarkContent);
                    sysRemarkService.updateSysRemark(parentRemark);
                }
            }
        }
        return toAjax(res);
    }

    /**
     * 修改评论
     */
    @PreAuthorize("@ss.hasPermi('system:remark:edit')")
    @Log(title = "评论", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysRemark sysRemark)
    {
        return toAjax(sysRemarkService.updateSysRemark(sysRemark));
    }

    /**
     * 删除评论
     */
    @PreAuthorize("@ss.hasPermi('system:remark:remove')")
    @Log(title = "评论", businessType = BusinessType.DELETE)
    @DeleteMapping("/{remarkIds}")
    public AjaxResult remove(@PathVariable Long[] remarkIds)
    {
        return toAjax(sysRemarkService.deleteSysRemarkByRemarkIds(remarkIds));
    }
    /**
     * 查询当前用户的评论列表
     */
    @PreAuthorize("@ss.hasPermi('system:remark:listByUsername')")
    @GetMapping("/listByUsername")
    public AjaxResult listByUsername() {
        long id=getUserId();
        String str = Long.toString(id);

        List<SysRemark> list = sysRemarkService.selectSysRemarkListByUsername(str);
        return success(list);
    }
    /**
     * 点赞或取消点赞评论
     */
    @PreAuthorize("@ss.hasPermi('system:remark:like')")
    @PostMapping("/{remarkId}")
    public AjaxResult likeOrCancelLike(@PathVariable Long remarkId) {
        long userId = getUserId();

        // 调用查询方法获取点赞状态
        SysUserRemark tmpt = new SysUserRemark();
        SysRemark sysRemark =sysRemarkService.selectSysRemarkByRemarkId(remarkId);
        Long likecnt=sysRemark.getLikeCnt();
        tmpt.setRemarkId(remarkId);
        tmpt.setUserId(userId);
        int likeOrReport = sysUserRemarkService.getLikeOrReportValue(tmpt);
        if (likeOrReport == 1) {
            // 如果已经点赞过，则取消点赞

            sysUserRemarkService.deleteSysUserRemarkByRemarkId(remarkId);
            sysRemark.setLikeCnt(likecnt-1L);
            sysRemarkService.updateSysRemark(sysRemark);
            return success("取消点赞成功");
        } else {
            // 如果未点赞，则点赞
            SysUserRemark sysUserRemark = new SysUserRemark();
            sysUserRemark.setRemarkId(remarkId);
            sysUserRemark.setUserId(userId);
            sysUserRemark.setLikeOrReport(1L); // 设置点赞值为 1
            sysUserRemarkService.insertSysUserRemark(sysUserRemark);
            sysRemark.setLikeCnt(likecnt+1L);
            sysRemarkService.updateSysRemark(sysRemark);
            return success("点赞成功");
        }
    }
    /**
     * 获取评论点赞状态
     */
    @PreAuthorize("@ss.hasPermi('system:remark:like')")
    @GetMapping("/getLikeValue/{remarkId}")
    public AjaxResult getLikeValue(@PathVariable Long remarkId) {
        long userId = getUserId();
        SysUserRemark tmpt = new SysUserRemark();
        tmpt.setRemarkId(remarkId);
        tmpt.setUserId(userId);
        int likeOrReport = sysUserRemarkService.getLikeOrReportValue(tmpt);
        return success(likeOrReport);
    }
    //zmjjkk 2024/5/14
    /**
     * 获取用户角色
     */
    @PreAuthorize("@ss.hasPermi('system:remark:add')")
    @GetMapping("/getUserRole")
    public AjaxResult getUserRole() {
        Long userId = getUserId();
        Long roleId = sysRoleService.selectRoleListByUserId(userId).get(0);
        return success(roleId);
    }
    //end

    /**
     * 处理举报评论 by jinx 20240514
     */
    @PreAuthorize("@ss.hasPermi('system:remark:like')")
    @PostMapping("/report/{remarkId}")
    public AjaxResult report(@PathVariable Long remarkId) {
        long userId = getUserId();

        // 调用查询方法获取举报状态
        SysUserRemark tmpt = new SysUserRemark();
        tmpt.setRemarkId(remarkId);
        tmpt.setUserId(userId);
        int reportValue = sysUserRemarkService.getReportValue(tmpt);
        if (reportValue == 1) {
            // 如果已经举报过，则直接返回举报成功
            return success("您已经举报过该评论");
        } else {
            // 如果未举报，则添加举报记录
            SysUserRemark sysUserRemark = new SysUserRemark();
            sysUserRemark.setRemarkId(remarkId);
            sysUserRemark.setUserId(userId);
            sysUserRemark.setLikeOrReport(2L); // 设置举报值为 2
            sysUserRemarkService.insertSysUserRemark(sysUserRemark);

            SysUser sysuser= userService.selectUserById(getUserId());
            String name =sysuser.getNickName();
            //处理举报 by jinx 20240514.2
            SysRemark temp=sysRemarkService.selectSysRemarkByRemarkId(remarkId);
            temp.setReportCnt(temp.getReportCnt()+1L);
            sysRemarkService.updateSysRemark(temp);
            System.out.println(temp.getReportCnt());

            // 将 remarkId 转换为字符串
            String remarkIdStr = String.valueOf(remarkId);
            // 调用 selectByRemark 方法检查是否存在相同 remarkId 的评论
            int result = noticeService.selectByRemark(remarkIdStr);
            // 如果返回值为0，则表示不存在相同 remarkId 的评论，可以执行插入操作
            if (result==0&&temp.getReportCnt() % 10 == 1){
                SysNotice sysnotice=new SysNotice();

                sysnotice.setNoticeId(remarkId);

                sysnotice.setCreateBy(name);
                String remarkContent = temp.getRemarkContent();
                if (remarkContent.startsWith(name + ":")) {
                    remarkContent = remarkContent.substring(name.length() + 1);
                }
                sysnotice.setNoticeTitle(remarkContent);
                // 设置sysnotice的noticeContent属性为处理后的字符串
                sysnotice.setRemark(remarkIdStr);

                sysnotice.setNoticeType(Integer.toString(1));
                if(temp.getReportCnt()>=50){
                    sysnotice.setNoticeType(Integer.toString(2));
                }
                noticeService.insertNotice(sysnotice);
            }

            return success("举报成功");
        }
    }

    private boolean containsProhibitedWords(String content) {
        // 可以扩展此列表以包含更多不当关键词
        String[] prohibitedWords = {  "sb", "fw", "傻逼", "傻B", "操你", "你妈", "妈的", "垃圾", "白痴", "蠢货", "笨蛋", "智障",
                "傻子", "废物", "狗屎", "滚蛋", "王八蛋", "婊子", "妓女", "贱人", "畜生", "死全家", "去死",
                "混蛋", "杂种", "狗逼", "狗日的", "贱货", "操你妈", "死妈", "妈逼", "草泥马" };
        for (String word : prohibitedWords) {
            if (content.toLowerCase().contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取第二级表项（课程+老师）的评论项的point均值
     * by firefly 20240514
     */
    @PreAuthorize("@ss.hasPermi('system:remark:avgPoint')")
    @GetMapping("/avgPoint/{remarkId}")
    public AjaxResult getAveragePoint(@PathVariable Long remarkId) {
        Double avgPoint = sysRemarkService.selectAveragePoint(remarkId);
        return success(avgPoint);
    }

    /**
     * 查询时间升序排列的评论列表
     * by firefly 20240515
     */
    @PreAuthorize("@ss.hasPermi('system:remark:like')")
    @GetMapping("/listByTimeASC")
    public AjaxResult listByTimeASC(SysRemark sysRemark)
    {
        List<SysRemark> list = sysRemarkService.selectSysRemarkListByTimeASC(sysRemark);
        return success(list);
    }

    /**
     * 查询时间降序排列的评论列表
     * by firefly 20240515
     */
    @PreAuthorize("@ss.hasPermi('system:remark:like')")
    @GetMapping("/listByTimeDESC")
    public AjaxResult listByTimeDESC(SysRemark sysRemark)
    {
        List<SysRemark> list = sysRemarkService.selectSysRemarkListByTimeDESC(sysRemark);
        return success(list);
    }

    /**
     *
     */
}
