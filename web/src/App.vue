<template>
  <router-view />
</template>

<style>
/* 与《方案》主色一致的点缀（Web 端允许 Element 默认主题，仅主色对齐 #DB2C1F 风格域） */
:root {
  --el-color-primary: #db2c1f;
}
html,
body,
#app {
  height: 100%;
  margin: 0;
  background: #f5f7fa;
}
</style>
