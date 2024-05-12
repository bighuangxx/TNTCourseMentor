<template>
  <div class="app-container">
    <!-- Search Form -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="名称" prop="remarkName">
        <el-input
          v-model="queryParams.remarkName"
          placeholder="请输入名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- Buttons Row -->
    <el-row :gutter="10" class="mb8">
      <!-- Add Remark Button -->
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:remark:add']"
        >新增</el-button>
      </el-col>
      <!-- Expand/Collapse Button -->
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <!-- My Remarks Button -->
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleFilterMyRemarks"
        >{{ filteringMyRemarks ? '取消筛选' : '我的评论' }}</el-button>
      </el-col>
      <!-- Right Toolbar -->
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- Remarks Table -->
    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="remarkList"
      row-key="remarkId"
      :default-expand-all="isExpandAll"
      :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
    >
      <el-table-column label="名称" align="center" prop="remarkName" />
      <el-table-column label="评论内容" align="center" prop="remarkContent" />
      <el-table-column label="得分" align="center" prop="point">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_star" :value="scope.row.point"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:remark:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAdd(scope.row)"
            v-hasPermi="['system:remark:add']"
          >新增</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:remark:remove']"
          >删除</el-button>
           <!-- Like Button -->
              <el-button
                v-if="scope.row.likeCnt === 0"
                size="mini"
                type="text"
                icon="el-icon-thumb-up"
                @click="handleLike(scope.row)"
              >点赞</el-button>
              <el-button
                v-else
                size="mini"
                type="text"
                icon="el-icon-thumb-up"
                @click="handleCancelLike(scope.row)"
              >已赞</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add/Update Remark Dialog -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="remarkName">
          <el-input v-model="form.remarkName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="评论内容">
          <editor v-model="form.remarkContent" :min-height="192"/>
        </el-form-item>
        <el-form-item label="得分" prop="point">
          <el-select v-model="form.point" placeholder="请选择得分">
            <el-option
              v-for="dict in dict.type.sys_star"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            ></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { listRemark, getRemark, delRemark, addRemark, updateRemark, listRemarkByUsername, likeOrCancelLike,getLikeValue } from "@/api/system/remark";
import Treeselect from "@riophae/vue-treeselect";
import "@riophae/vue-treeselect/dist/vue-treeselect.css";

export default {
  name: "Remark",
  dicts: ['sys_star'],
  components: {
    Treeselect
  },
  data() {
    return {
      loading: true,
      showSearch: true,
      filteringMyRemarks: false,
      remarkList: [],
      remarkOptions: [],
      title: "",
      open: false,
      isExpandAll: true,
      refreshTable: true,
      queryParams: {
        remarkName: null,
      },
      form: {},
      rules: {}
    };
  },
  created() {
    this.getList();
this.fetchLikeStatus();
  },
  methods: {
    handleLike(row) {
      likeOrCancelLike(row.remarkId).then(response => {
        if (response.code === 200) {
          row.likeCnt = 1;
          this.$message.success("点赞成功");
        } else {
          this.$message.error(response.msg);
        }
      });
    },
    handleCancelLike(row) {
      likeOrCancelLike(row.remarkId).then(response => {
        if (response.code === 200) {
          row.likeCnt = 0;
          this.$message.success("取消点赞成功");
        } else {
          this.$message.error(response.msg);
        }
      });
    },
    fetchLikeStatus() {
      const traverseComments = (comments) => {
        comments.forEach(comment => {
          // 获取当前评论的点赞状态
          getLikeValue(comment.remarkId)
            .then(response => {
              comment.likeCnt = response.data === 1 ? 1 : 0;
              console.log(`Remark ID: ${comment.remarkId}, Like Count: ${comment.likeCnt}`);
            })
            .catch(error => {
              console.error("Error fetching like status for remarkId", comment.remarkId, ":", error);
            });

          // 递归获取子评论的点赞状态
          if (comment.children && comment.children.length > 0) {
            traverseComments(comment.children);
          }
        });
      };

      console.log("Fetching like status for remarkList:", this.remarkList);
      traverseComments(this.remarkList);
    },



    listMyRemarks() {
      this.loading = true;
      listRemarkByUsername().then(response => {
        this.remarkList = this.handleTree(response.data, "remarkId", "parentId");
        this.loading = false;
        this.fetchLikeStatus(); // Fetch like status after getting remarkList
      });
    },
    getList() {
      this.loading = true;
      if (this.filteringMyRemarks) {
        this.listMyRemarks();
      } else {
        listRemark(this.queryParams).then(response => {
          this.remarkList = this.handleTree(response.data, "remarkId", "parentId");
          console.log("Number of remarks in remarkList:", this.remarkList.length);
          this.loading = false;
          this.fetchLikeStatus(); // Fetch like status after getting remarkList
        });
      }
    },
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children;
      }
      return {
        id: node.remarkId,
        label: node.remarkName,
        children: node.children
      };
    },
    getTreeselect() {
      listRemark().then(response => {
        this.remarkOptions = [];
        const data = { remarkId: 0, remarkName: '顶级节点', children: [] };
        data.children = this.handleTree(response.data, "remarkId", "parentId");
        this.remarkOptions.push(data);
      });
    },
    cancel() {
      this.open = false;
      this.reset();
    },
    reset() {
      this.form = {
        remarkId: null,
        parentId: null,
        ancestors: null,
        stat: null,
        remarkName: null,
        delFlag: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null,
        remarkContent: null,
        point: null,
        likeCnt: null, // Add likeCnt property to the form
        reportCnt: null,
        reservedPort1: null,
        reservedPort2: null,
        reservedPort3: null
      };
      this.resetForm("form");
    },
    handleQuery() {
      this.getList();
    },
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleAdd(row) {
      this.reset();
      this.getTreeselect();
      if (row != null && row.remarkId) {
        this.form.parentId = row.remarkId;
      } else {
        this.form.parentId = 0;
      }
      this.open = true;
      this.title = "添加评论";
      console.log("Number of remarks in remarkList:", this.remarkList.length);
    },
    toggleExpandAll() {
      this.refreshTable = false;
      this.isExpandAll = !this.isExpandAll;
      this.$nextTick(() => {
        this.refreshTable = true;
      });
    },
    handleUpdate(row) {
      this.reset();
      this.getTreeselect();
      if (row != null) {
        this.form.parentId = row.parentId;
      }
      getRemark(row.remarkId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改评论";
      });
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.remarkId != null) {
            updateRemark(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addRemark(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除评论编号为"' + row.remarkId + '"的数据项？').then(() => {
        return delRemark(row.remarkId);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    toggleFilterMyRemarks() {
      this.filteringMyRemarks = !this.filteringMyRemarks;
      this.getList();
    }
  }
};
</script>
