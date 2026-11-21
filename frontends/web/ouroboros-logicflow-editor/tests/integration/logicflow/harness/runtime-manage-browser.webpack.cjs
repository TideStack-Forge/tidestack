const path = require('node:path')
const configure = require('../../../../webpack.config.js')
const HtmlWebpackPlugin = require('html-webpack-plugin')
module.exports = () => {
  const configuration = configure({}, {mode:'development'})
  configuration.entry = {host:path.resolve(__dirname,'runtime-manage-browser.jsx')}
  configuration.output = {...configuration.output,path:path.resolve(__dirname,'../../../../../../../tmp/testing-evidence/logicflow-runtime-manage/browser-host'),publicPath:'/',uniqueName:'runtime-manage-acceptance-host',chunkLoadingGlobal:'webpackChunkRuntimeManageAcceptanceHost'}
  configuration.plugins = [new HtmlWebpackPlugin({templateContent:'<!doctype html><html lang="zh"><head><meta charset="utf-8"><title>逻辑流运行时管理验收</title></head><body class="amis-scope" style="margin:0"><aside id="fixture-status" style="background:#17293c;color:#dbeafe;padding:12px 24px;font:13px system-ui">正在加载验收宿主…</aside><div id="notification" role="status"></div><div id="root"></div><svg id="__SVG_SPRITE_NODE__" style="display:none"></svg></body></html>'})]
  return configuration
}
