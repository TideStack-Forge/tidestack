const path = require('node:path')
const configure = require('../../../../webpack.config.js')
const HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = () => {
  const configuration = configure({}, { mode: 'development' })
  configuration.entry = { app: path.resolve(__dirname, 'runtime-debug-browser.jsx') }
  configuration.output.path = path.resolve(__dirname, '../../../../../../../tmp/testing-evidence/logicflow-online-debugging/browser-bundle')
  configuration.output.publicPath = '/'
  configuration.plugins = [new HtmlWebpackPlugin({ templateContent: '<!doctype html><html lang="zh"><head><meta charset="utf-8"><title>逻辑流调试验收</title></head><body style="margin:0"><div id="root"></div></body></html>' })]
  configuration.devServer = {
    ...configuration.devServer, host: '127.0.0.1', port: 18879,
    static: false, devMiddleware: { publicPath: '/' }, client: { overlay: { errors: true, warnings: false } },
    proxy: [{ context: ['/api', '/fixture'], target: 'http://127.0.0.1:18878' }],
  }
  return configuration
}
