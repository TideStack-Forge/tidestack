const path = require('path')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')

const packageJson = require('./package.json')
const port = process.env.port || process.env.npm_config_port || 8081
const publicPath = '/debug-console-header/'

module.exports = (env, argv = {}) => {
  const isProduction = argv.mode === 'production'

  return {
    mode: isProduction ? 'production' : 'development',
    entry: {
      app: path.resolve(__dirname, 'src/main.js'),
    },
    output: {
      path: path.resolve(__dirname, 'dist'),
      filename: 'static/js/[name].js',
      chunkFilename: 'static/js/[name].js',
      assetModuleFilename: 'static/[name][ext][query]',
      publicPath,
      clean: true,
      library: `${packageJson.name}-[name]`,
      libraryTarget: 'umd',
      globalObject: 'self',
    },
    devtool: isProduction ? false : 'eval-cheap-module-source-map',
    resolve: {
      extensions: ['.js', '.jsx', '.ts', '.tsx', '.json'],
      alias: {
        '@': path.resolve(__dirname, 'src'),
        'ouroboros-sdk': path.resolve(__dirname, '../ouroboros-sdk/src/main.ts'),
      },
    },
    module: {
      rules: [
        {
          test: /\.(js|jsx|ts|tsx)$/,
          exclude: /node_modules/,
          use: {
            loader: 'babel-loader',
          },
        },
        {
          test: /\.(sa|sc|c)ss$/,
          use: [
            isProduction ? MiniCssExtractPlugin.loader : 'style-loader',
            'css-loader',
            'sass-loader',
          ],
        },
      ],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: path.resolve(__dirname, 'public/index.html'),
      }),
      new MiniCssExtractPlugin({
        filename: 'static/css/[name].css',
        chunkFilename: 'static/css/[name].css',
      }),
    ],
    externals: {
      react: 'React',
      'react-dom': 'ReactDOM',
    },
    optimization: {
      splitChunks: false,
      runtimeChunk: false,
    },
    devServer: {
      port,
      open: false,
      headers: {
        'Access-Control-Allow-Origin': '*',
      },
      static: {
        directory: path.resolve(__dirname, 'dist'),
        publicPath,
        watch: false,
      },
      devMiddleware: {
        publicPath,
      },
    },
    performance: {
      hints: false,
    },
    stats: 'minimal',
  }
}
