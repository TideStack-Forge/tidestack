(function (global) {
  var UI_SCHEMA_NAME_LABEL_REMARK = '按低代码页面命名习惯填写点分名称，例如 Admin.UserList；系统运行时会映射成 /admin/user-list。';
  var IMPORT_CONFIGS = {
    i18n: {
      hint: '表头：namespace, key, value。导入后合并到 messages。',
    },
    'data-dict': {
      hint: '表头：code, label, order, parent；其他列进入 extraProps。',
    },
    'data-init': {
      hint: '表头：blockId, targetModel, matchFields；其他列作为 records 字段。',
    },
  };

  function byId(id) {
    return global.document.getElementById(id);
  }

  function isRecord(value) {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
  }

  function trimImportCell(value) {
    return value === undefined || value === null ? '' : String(value).replace(/^\uFEFF/, '').trim();
  }

  function normalizeImportHeader(value) {
    return trimImportCell(value).toLowerCase().replace(/[\s_.\-]/g, '');
  }

  function hasImportValue(value) {
    return trimImportCell(value) !== '';
  }

  function splitImportList(value) {
    return trimImportCell(value)
      .split(/[,，;；]/)
      .map(function (item) { return item.trim(); })
      .filter(Boolean);
  }

  function parseImportScalar(value) {
    var text = trimImportCell(value);
    if (text === '') {
      return undefined;
    }
    if (text === 'true') {
      return true;
    }
    if (text === 'false') {
      return false;
    }
    if (text === 'null') {
      return null;
    }
    if ((text[0] === '{' && text[text.length - 1] === '}') || (text[0] === '[' && text[text.length - 1] === ']')) {
      try {
        return JSON.parse(text);
      } catch (_error) {
        return text;
      }
    }
    if (/^-?(0|[1-9]\d*)(\.\d+)?$/.test(text)) {
      var numberValue = Number(text);
      if (Number.isFinite(numberValue)) {
        return numberValue;
      }
    }
    return text;
  }

  function normalizeDocument(value) {
    if (Array.isArray(value)) {
      return value.map(normalizeDocument);
    }
    if (!isRecord(value)) {
      return value;
    }
    return Object.keys(value)
      .reduce(function (result, key) {
        result[key] = normalizeDocument(value[key]);
        return result;
      }, {});
  }

  function serializeDocument(value) {
    var serialized = JSON.stringify(normalizeDocument(value), null, 2);
    return (serialized === undefined ? '' : serialized) + '\n';
  }

  function detectDelimitedSeparator(text) {
    var sampleLines = String(text || '').split(/\r?\n/).slice(0, 10).filter(function (line) {
      return line.trim() !== '';
    });
    var sample = sampleLines.join('\n');
    var separators = [',', '\t', ';'];
    var best = ',';
    var bestCount = -1;
    separators.forEach(function (separator) {
      var count = sample.split(separator).length - 1;
      if (count > bestCount) {
        best = separator;
        bestCount = count;
      }
    });
    return best;
  }

  function parseDelimitedRows(text) {
    var source = String(text || '').replace(/^\uFEFF/, '');
    var separator = detectDelimitedSeparator(source);
    var rows = [];
    var row = [];
    var cell = '';
    var quoted = false;
    for (var index = 0; index < source.length; index += 1) {
      var character = source[index];
      if (quoted) {
        if (character === '"') {
          if (source[index + 1] === '"') {
            cell += '"';
            index += 1;
          } else {
            quoted = false;
          }
        } else {
          cell += character;
        }
        continue;
      }
      if (character === '"') {
        quoted = true;
        continue;
      }
      if (character === separator) {
        row.push(cell);
        cell = '';
        continue;
      }
      if (character === '\r' || character === '\n') {
        row.push(cell);
        rows.push(row);
        row = [];
        cell = '';
        if (character === '\r' && source[index + 1] === '\n') {
          index += 1;
        }
        continue;
      }
      cell += character;
    }
    if (quoted) {
      throw new Error('CSV 文件存在未闭合的引号。');
    }
    if (cell !== '' || row.length || source.endsWith(separator)) {
      row.push(cell);
      rows.push(row);
    }
    return rows;
  }

  function createImportTable(matrix) {
    var rows = Array.isArray(matrix) ? matrix : [];
    var headerRowIndex = rows.findIndex(function (row) {
      return Array.isArray(row) && row.some(hasImportValue);
    });
    if (headerRowIndex < 0) {
      throw new Error('导入文件没有可识别的表头。');
    }
    var headerEntries = rows[headerRowIndex]
      .map(function (header, index) {
        return { index: index, name: trimImportCell(header) };
      })
      .filter(function (entry) { return entry.name !== ''; });
    if (!headerEntries.length) {
      throw new Error('导入文件没有可识别的表头。');
    }
    var records = rows.slice(headerRowIndex + 1).reduce(function (result, row) {
      if (!Array.isArray(row) || !row.some(hasImportValue)) {
        return result;
      }
      var record = {};
      headerEntries.forEach(function (entry) {
        record[entry.name] = row[entry.index] === undefined || row[entry.index] === null ? '' : String(row[entry.index]);
      });
      result.push(record);
      return result;
    }, []);
    return {
      headers: headerEntries.map(function (entry) { return entry.name; }),
      rows: records,
    };
  }

  function findImportColumn(headers, aliases) {
    var normalizedAliases = aliases.map(normalizeImportHeader);
    return headers.find(function (header) {
      return normalizedAliases.indexOf(normalizeImportHeader(header)) >= 0;
    });
  }

  function requireImportColumn(table, aliases, label) {
    var column = findImportColumn(table.headers, aliases);
    if (!column) {
      throw new Error('导入文件缺少必需列：' + label + '。');
    }
    return column;
  }

  function stripRecordFieldPrefix(header) {
    return trimImportCell(header).replace(/^(record|records|field)[._-]/i, '');
  }

  function stripExtraPropPrefix(header) {
    return trimImportCell(header).replace(/^(extraProps|extra|props)[._-]/i, '');
  }

  function createI18nImportResult(table) {
    var namespaceColumn = requireImportColumn(table, ['namespace', 'ns', '命名空间'], 'namespace');
    var keyColumn = requireImportColumn(table, ['key', 'messageKey', '消息键', '键'], 'key');
    var valueColumn = requireImportColumn(table, ['value', 'message', 'text', 'messageText', '消息文本', '文案', '值'], 'value');
    var messages = {};
    var count = 0;
    table.rows.forEach(function (row, index) {
      var namespace = trimImportCell(row[namespaceColumn]) || 'common';
      var key = trimImportCell(row[keyColumn]);
      if (!key) {
        throw new Error('第 ' + (index + 2) + ' 行缺少消息键。');
      }
      if (!isRecord(messages[namespace])) {
        messages[namespace] = {};
      }
      messages[namespace][key] = row[valueColumn] === undefined || row[valueColumn] === null ? '' : String(row[valueColumn]);
      count += 1;
    });
    return {
      value: { messages: messages },
      replaceKeys: [],
      summary: '已导入 ' + count + ' 条国际化消息。',
    };
  }

  function createDataDictImportResult(table) {
    var codeColumn = requireImportColumn(table, ['code', 'value', '编码', '代码'], 'code');
    var labelColumn = requireImportColumn(table, ['label', 'title', 'name', '标签', '名称', '标题'], 'label');
    var orderColumn = findImportColumn(table.headers, ['order', 'sort', '排序']);
    var parentColumn = findImportColumn(table.headers, ['parent', 'parentCode', 'parentId', '父级编码', '父级']);
    var reserved = [codeColumn, labelColumn, orderColumn, parentColumn].filter(Boolean);
    var items = table.rows.map(function (row, index) {
      var code = trimImportCell(row[codeColumn]);
      var label = trimImportCell(row[labelColumn]);
      if (!code) {
        throw new Error('第 ' + (index + 2) + ' 行缺少字典项编码。');
      }
      if (!label) {
        throw new Error('第 ' + (index + 2) + ' 行缺少字典项标签。');
      }
      var item = { code: code, label: label };
      if (orderColumn && hasImportValue(row[orderColumn])) {
        var order = Number(trimImportCell(row[orderColumn]));
        if (!Number.isFinite(order)) {
          throw new Error('第 ' + (index + 2) + ' 行排序值不是数字。');
        }
        item.order = Math.trunc(order);
      }
      if (parentColumn && hasImportValue(row[parentColumn])) {
        item.parent = trimImportCell(row[parentColumn]);
      }
      var extraProps = {};
      table.headers.forEach(function (header) {
        if (reserved.indexOf(header) >= 0 || !hasImportValue(row[header])) {
          return;
        }
        var propName = stripExtraPropPrefix(header);
        if (propName) {
          extraProps[propName] = parseImportScalar(row[header]);
        }
      });
      if (Object.keys(extraProps).length) {
        item.extraProps = extraProps;
      }
      return item;
    });
    return {
      value: { items: items },
      replaceKeys: ['items'],
      summary: '已导入 ' + items.length + ' 个字典项。',
    };
  }

  function getSingleCurrentBlock(currentValue) {
    return isRecord(currentValue) && Array.isArray(currentValue.blocks) && currentValue.blocks.length === 1 && isRecord(currentValue.blocks[0])
      ? currentValue.blocks[0]
      : undefined;
  }

  function createDataInitImportResult(table, currentValue) {
    var blockIdColumn = findImportColumn(table.headers, ['blockId', 'block', 'blockName', '初始化块', '块']);
    var targetModelColumn = findImportColumn(table.headers, ['targetModel', 'model', 'modelName', '目标模型', '模型']);
    var matchFieldsColumn = findImportColumn(table.headers, ['matchFields', 'matchField', 'match', '匹配字段']);
    var blockVersionColumn = findImportColumn(table.headers, ['blockVersion', '初始化块版本']);
    var reserved = [blockIdColumn, targetModelColumn, matchFieldsColumn, blockVersionColumn].filter(Boolean);
    var currentBlock = getSingleCurrentBlock(currentValue);
    var blockOrder = [];
    var blocksById = {};
    table.rows.forEach(function (row, index) {
      var blockId = blockIdColumn ? trimImportCell(row[blockIdColumn]) : '';
      blockId = blockId || (currentBlock && typeof currentBlock.id === 'string' ? currentBlock.id : 'records');
      var targetModel = targetModelColumn ? trimImportCell(row[targetModelColumn]) : '';
      targetModel = targetModel || (currentBlock && typeof currentBlock.targetModel === 'string' ? currentBlock.targetModel : '');
      if (!targetModel) {
        throw new Error('第 ' + (index + 2) + ' 行缺少目标模型。');
      }
      if (!blocksById[blockId]) {
        blockOrder.push(blockId);
        blocksById[blockId] = {
          id: blockId,
          targetModel: targetModel,
          matchFields: [],
          records: [],
        };
      }
      var block = blocksById[blockId];
      if (targetModel && block.targetModel !== targetModel) {
        throw new Error('初始化块 ' + blockId + ' 的目标模型不一致。');
      }
      if (matchFieldsColumn && hasImportValue(row[matchFieldsColumn])) {
        block.matchFields = splitImportList(row[matchFieldsColumn]);
      } else if (!block.matchFields.length && currentBlock && Array.isArray(currentBlock.matchFields)) {
        block.matchFields = currentBlock.matchFields.slice();
      }
      if (blockVersionColumn && hasImportValue(row[blockVersionColumn])) {
        block.version = trimImportCell(row[blockVersionColumn]);
      }
      var record = {};
      table.headers.forEach(function (header) {
        if (reserved.indexOf(header) >= 0 || !hasImportValue(row[header])) {
          return;
        }
        var fieldName = stripRecordFieldPrefix(header);
        if (fieldName) {
          record[fieldName] = parseImportScalar(row[header]);
        }
      });
      if (!Object.keys(record).length) {
        throw new Error('第 ' + (index + 2) + ' 行没有可导入的记录字段。');
      }
      block.records.push(record);
    });
    var blocks = blockOrder.map(function (blockId) {
      var block = blocksById[blockId];
      if (!block.matchFields.length) {
        delete block.matchFields;
      }
      return block;
    });
    return {
      value: { blocks: blocks },
      replaceKeys: ['blocks'],
      summary: '已导入 ' + blocks.length + ' 个初始化块、' + table.rows.length + ' 条记录。',
    };
  }

  function createMetadataImportResult(metadataType, matrix, currentValue) {
    var table = createImportTable(matrix);
    if (!table.rows.length) {
      throw new Error('导入文件没有可导入的数据行。');
    }
    if (metadataType === 'i18n') {
      return createI18nImportResult(table, currentValue);
    }
    if (metadataType === 'data-dict') {
      return createDataDictImportResult(table, currentValue);
    }
    if (metadataType === 'data-init') {
      return createDataInitImportResult(table, currentValue);
    }
    throw new Error('当前元数据类型不支持导入。');
  }

  function mergeImportValue(current, patch, replaceKeys) {
    var replacements = replaceKeys || [];
    if (!isRecord(current) || !isRecord(patch)) {
      return normalizeDocument(patch);
    }
    var next = {};
    Object.keys(current).forEach(function (key) {
      if (!hasOwn(patch, key)) {
        next[key] = normalizeDocument(current[key]);
      } else if (replacements.indexOf(key) >= 0) {
        next[key] = normalizeDocument(patch[key]);
      } else {
        next[key] = mergePreservingKeyOrder(current[key], patch[key]);
      }
    });
    Object.keys(patch).forEach(function (key) {
      if (!hasOwn(current, key)) {
        next[key] = normalizeDocument(patch[key]);
      }
    });
    return next;
  }

  function isSupportedImportType(metadataType) {
    return hasOwn(IMPORT_CONFIGS, metadataType);
  }

  function readImportFileAsText(file) {
    return new Promise(function (resolve, reject) {
      if (typeof global.FileReader !== 'function') {
        reject(new Error('当前 IDE 浏览器不支持读取导入文件。'));
        return;
      }
      var reader = new global.FileReader();
      reader.onload = function () { resolve(reader.result === undefined || reader.result === null ? '' : String(reader.result)); };
      reader.onerror = function () { reject(reader.error || new Error('读取导入文件失败。')); };
      reader.readAsText(file, 'utf-8');
    });
  }

  function readImportFileAsArrayBuffer(file) {
    return new Promise(function (resolve, reject) {
      if (typeof global.FileReader !== 'function') {
        reject(new Error('当前 IDE 浏览器不支持读取导入文件。'));
        return;
      }
      var reader = new global.FileReader();
      reader.onload = function () { resolve(reader.result); };
      reader.onerror = function () { reject(reader.error || new Error('读取导入文件失败。')); };
      reader.readAsArrayBuffer(file);
    });
  }

  function parseWorkbookRows(buffer) {
    var XLSX = global.XLSX;
    if (!XLSX || typeof XLSX.read !== 'function' || !XLSX.utils || typeof XLSX.utils.sheet_to_json !== 'function') {
      throw new Error('Excel 解析运行时未加载。');
    }
    var workbook = XLSX.read(new Uint8Array(buffer), { type: 'array' });
    var firstSheetName = workbook && workbook.SheetNames && workbook.SheetNames[0];
    if (!firstSheetName || !workbook.Sheets || !workbook.Sheets[firstSheetName]) {
      throw new Error('Excel 文件没有可导入的工作表。');
    }
    return XLSX.utils.sheet_to_json(workbook.Sheets[firstSheetName], { header: 1, defval: '' });
  }

  function readImportRowsFromFile(file) {
    var name = file && typeof file.name === 'string' ? file.name : '';
    if (/\.csv$/i.test(name) || file.type === 'text/csv') {
      return readImportFileAsText(file).then(parseDelimitedRows);
    }
    if (/\.(xlsx|xls)$/i.test(name)) {
      return readImportFileAsArrayBuffer(file).then(parseWorkbookRows);
    }
    return Promise.reject(new Error('仅支持 .csv、.xlsx 或 .xls 文件。'));
  }

  function formatDiagnostics(items) {
    if (!items || !items.length) {
      return '';
    }
    return items
      .map(function (item) {
        return item.severity + ': ' + item.code + ' - ' + item.message;
      })
      .join('\n');
  }

  function clone(value) {
    return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
  }

  function normalizeEditorSchema(schema) {
    var next = clone(schema);
    if (isRecord(next) && next.type === 'form' && next.actions === undefined) {
      next.actions = [];
    }
    if (isRecord(next) && next.type === 'form' && next.title === undefined) {
      next.title = false;
    }
    return next;
  }

  function getMetadataRootField(state) {
    return isRecord(state.editorSchema) && typeof state.editorSchema.metadataRootField === 'string' && state.editorSchema.metadataRootField
      ? state.editorSchema.metadataRootField
      : undefined;
  }

  function getEditorFormData(state) {
    var rootField = getMetadataRootField(state);
    if (rootField) {
      var wrappedData = {};
      wrappedData[rootField] = state.currentValue;
      return wrappedData;
    }
    return state.documentShape === 'array'
      ? { items: Array.isArray(state.currentValue) ? state.currentValue : [] }
      : isRecord(state.currentValue) ? state.currentValue : {};
  }

  function unwrapMetadataRootValue(state, value) {
    var rootField = getMetadataRootField(state);
    if (!rootField) {
      return value;
    }
    if (isRecord(value) && hasOwn(value, rootField)) {
      return value[rootField];
    }
    return value;
  }

  function extractValueFromAmisChange(args) {
    var first = args[0];
    var second = args[1];
    var third = args[2];
    if (isRecord(first) && isRecord(second) && isRecord(third)) {
      return first;
    }
    if (typeof second === 'string') {
      var next = {};
      next[second] = first;
      return next;
    }
    if (isRecord(first) && isRecord(first.data)) {
      return first.data;
    }
    if (isRecord(first) && isRecord(first.values)) {
      return first.values;
    }
    if (isRecord(first)) {
      return first;
    }
    if (isRecord(third)) {
      return third;
    }
    return undefined;
  }

  function getAmisEmbed() {
    if (global.OuroborosAmisEditorRuntime && typeof global.OuroborosAmisEditorRuntime.renderSchema === 'function') {
      return global.OuroborosAmisEditorRuntime.renderSchema;
    }
    if (!global.amisRequire) {
      return undefined;
    }
    var module = global.amisRequire('amis/embed');
    if (module && typeof module.embed === 'function') {
      return module.embed;
    }
    if (typeof module === 'function') {
      return module;
    }
    if (module && module.default && typeof module.default.embed === 'function') {
      return module.default.embed;
    }
    return undefined;
  }

  var IDE_FALLBACK_FORM_ITEM_TYPES = [
    'ouroboros-icon-picker',
    'tabs-transfer-editor',
    'amis-schema-editor',
    'amis-editor',
    'amis-schema',
    'amis-schema-preview',
    'input-kvs',
    'populate-editor',
    'where-editor',
    'script-editor',
  ];

  function registerIdeFallbackFormItems() {
    if (global.__ouroborosMetadataIdeFallbackFormItemsRegistered || !global.amisRequire) {
      return;
    }
    var amis;
    var React;
    try {
      amis = global.amisRequire('amis');
      React = global.React || global.amisRequire('react');
    } catch (_error) {
      return;
    }
    if (!amis || !React || typeof amis.FormItem !== 'function') {
      return;
    }
    global.__ouroborosMetadataIdeFallbackFormItemsRegistered = true;

    var structuredTypes = {
      'tabs-transfer-editor': true,
      'amis-schema-editor': true,
      'amis-editor': true,
      'amis-schema': true,
      'amis-schema-preview': true,
      'input-kvs': true,
      'populate-editor': true,
      'where-editor': true,
    };

    function formatFallbackValue(value) {
      if (value === undefined || value === null) {
        return '';
      }
      if (typeof value === 'string') {
        return value;
      }
      try {
        return JSON.stringify(value, null, 2);
      } catch (_error) {
        return String(value);
      }
    }

    function isStructuredFallbackValue(props) {
      return Boolean(structuredTypes[props.type]) || Array.isArray(props.value)
        || (props.value !== null && typeof props.value === 'object');
    }

    class IdeFallbackFormItem extends React.Component {
      constructor(props) {
        super(props);
        this.state = {
          text: formatFallbackValue(props.value),
          parseError: '',
          focused: false,
        };
        this.handleChange = this.handleChange.bind(this);
        this.handleFocus = this.handleFocus.bind(this);
        this.handleBlur = this.handleBlur.bind(this);
      }

      componentDidUpdate(previousProps) {
        if (previousProps.value !== this.props.value) {
          var nextText = formatFallbackValue(this.props.value);
          if (nextText !== this.state.text) {
            this.setState({ text: nextText, parseError: '' });
          }
        }
      }

      handleChange(event) {
        var text = event && event.target ? event.target.value : '';
        this.setState({ text: text });
        if (!this.props.onChange) {
          return;
        }
        if (!isStructuredFallbackValue(this.props)) {
          this.setState({ parseError: '' });
          this.props.onChange(text);
          return;
        }
        if (!text.trim()) {
          this.setState({ parseError: '' });
          this.props.onChange(undefined);
          return;
        }
        try {
          this.props.onChange(JSON.parse(text));
          this.setState({ parseError: '' });
        } catch (_error) {
          this.setState({ parseError: '请输入合法 JSON。' });
        }
      }

      handleFocus() {
        this.setState({ focused: true });
      }

      handleBlur() {
        this.setState({ focused: false });
      }

      renderTextInput(disabled, placeholder) {
        var classPrefix = this.props.classPrefix || 'cxd-';
        var className = this.props.className || '';
        var controlClassName = [
          className,
          classPrefix + 'TextControl',
          'metadata-ide-fallback-text-control',
          this.state.focused ? 'is-focused' : '',
          disabled ? 'is-disabled' : '',
        ].filter(Boolean).join(' ');
        return React.createElement(
          'div',
          { className: controlClassName },
          React.createElement(
            'div',
            { className: classPrefix + 'TextControl-input metadata-ide-fallback-text-input' },
            React.createElement('input', {
              type: 'text',
              name: this.props.name,
              value: this.state.text,
              disabled: disabled,
              placeholder: placeholder,
              autoComplete: 'off',
              onChange: this.handleChange,
              onFocus: this.handleFocus,
              onBlur: this.handleBlur,
            }),
          ),
        );
      }

      render() {
        var isStructured = isStructuredFallbackValue(this.props);
        var disabled = Boolean(this.props.disabled || this.props.readOnly || this.props.static);
        var className = this.props.className || '';
        var placeholder = this.props.placeholder || (isStructured ? '当前 IDE 使用 JSON 方式编辑此配置。' : '请输入值');
        var inputStyle = {
          width: '100%',
          minHeight: isStructured ? 120 : undefined,
          boxSizing: 'border-box',
          fontFamily: isStructured ? 'var(--vscode-editor-font-family, monospace)' : undefined,
        };
        return React.createElement(
          'div',
          { className: className },
          isStructured
            ? React.createElement('textarea', {
                value: this.state.text,
                disabled: disabled,
                placeholder: placeholder,
                onChange: this.handleChange,
                style: inputStyle,
              })
            : this.renderTextInput(disabled, placeholder),
          this.state.parseError
            ? React.createElement('div', { style: { color: '#c00000', fontSize: 12, marginTop: 4 } }, this.state.parseError)
            : null,
        );
      }
    }

    IDE_FALLBACK_FORM_ITEM_TYPES.forEach(function (type) {
      try {
        if (typeof amis.getRendererByName === 'function' && amis.getRendererByName(type)) {
          return;
        }
        amis.FormItem({ type: type, autoVar: true })(IdeFallbackFormItem);
      } catch (error) {
        if (global.console && typeof global.console.warn === 'function') {
          global.console.warn('Unable to register IDE fallback renderer ' + type, error);
        }
      }
    });
  }

  function createObject(superProps, props, properties) {
    var amisCore;
    if (global.amisRequire) {
      try {
        amisCore = global.amisRequire('amis-core');
      } catch (_error) {}
    }
    if (amisCore && typeof amisCore.createObject === 'function') {
      return amisCore.createObject(superProps, props, properties);
    }
    var result = Object.assign({}, superProps || {}, props || {});
    if (properties) {
      Object.keys(properties).forEach(function (key) {
        Object.defineProperty(result, key, properties[key]);
      });
    }
    return result;
  }

  function notify(type, message) {
    var normalizedType = ['success', 'info', 'warning', 'error'].indexOf(type) >= 0 ? type : 'info';
    if (global.console && typeof global.console[normalizedType === 'warning' ? 'warn' : normalizedType] === 'function') {
      global.console[normalizedType === 'warning' ? 'warn' : normalizedType](message);
    }
  }

  function findCandidateSourceItem(candidateName) {
    var integrations = getEditorIntegrations();
    var source = integrations.approvalWorkflowCandidateSource || {};
    var items = Array.isArray(source.items) ? source.items : [];
    return items.find(function (item) { return item && item.name === candidateName; });
  }

  function getBusinessFieldTypes() {
    var integrations = getEditorIntegrations();
    return isRecord(integrations.businessFieldTypes) ? integrations.businessFieldTypes : {};
  }

  function businessFieldTypeOptions(kind) {
    var source = getBusinessFieldTypes();
    var items = Array.isArray(source[kind]) ? source[kind] : [];
    return { items: items };
  }

  function businessFieldTypeUiSchema(fieldType) {
    var source = getBusinessFieldTypes();
    var uiSchemas = isRecord(source.uiSchemas) ? source.uiSchemas : {};
    var body = Array.isArray(uiSchemas[fieldType]) ? uiSchemas[fieldType] : [];
    return { type: 'container', body: body };
  }

  function getEditorIntegrations() {
    return isRecord(global.OuroborosMetadataEditorIntegrations)
      ? global.OuroborosMetadataEditorIntegrations
      : {};
  }

  function normalizeFetcherRequest(api) {
    if (typeof api === 'string') {
      return { url: api };
    }
    if (isRecord(api)) {
      return { url: typeof api.url === 'string' ? api.url : '', api: api };
    }
    return { url: '' };
  }

  function normalizeIntegrationUrl(url) {
    return String(url || '').replace(/^(get|post|put|delete|patch):(?=ide:\/\/|\/)/i, '');
  }

  function safeDecodeURIComponent(value) {
    try {
      return decodeURIComponent(value);
    } catch (_error) {
      return value;
    }
  }

  function integrationResponse(data) {
    return Promise.resolve({ status: 0, msg: '', data: clone(data) });
  }

  function emptyIntegrationResponse(message) {
    return Promise.resolve({ status: 0, msg: message || '', data: { items: [] } });
  }

  function createEditorIntegrationFetcher() {
    return function (api, data) {
      var request = normalizeFetcherRequest(api);
      var url = normalizeIntegrationUrl(request.url);
      var integrations = getEditorIntegrations();
      var host = global.OuroborosMetadataEditorHost;
      if (host && host.service && typeof host.requestService === 'function') {
        var appName = encodeURIComponent(host.service.appName);
        url = url.replace('ide://editor-integrations/logicflow/node-defines', '/api/dev/' + appName + '/logicflow/node-define')
          .replace('ide://editor-integrations/business/field-types/', '/api/app/' + appName + '/field-types/')
          .replace('ide://editor-integrations/approval-workflow/', '/api/dev/' + appName + '/approval-workflow/');
        return host.requestService({ url: url, method: request.api && request.api.method || 'GET',
          data: request.api && request.api.data !== undefined ? request.api.data : data });
      }
      if (/^\/api\//.test(url) && /code-intelligence/.test(url)) {
        return Promise.reject(new Error('Configure an editor service to enable code intelligence.'));
      }
      if (!url) {
        return emptyIntegrationResponse('IDE metadata editor received an empty source URL.');
      }
      if (url === 'ide://editor-integrations/logicflow/node-defines' || url.indexOf('/logicflow/node-define') >= 0) {
        return integrationResponse(integrations.logicflowNodeDefines || { items: [] });
      }
      if (url === 'ide://editor-integrations/approval-workflow/candidate') {
        return integrationResponse(integrations.approvalWorkflowCandidateSource || { items: [] });
      }
      if (url === 'ide://editor-integrations/approval-workflow/form-fields') {
        return integrationResponse(integrations.approvalWorkflowFormFieldSource || { fields: [] });
      }
      var candidateItemsMatch = url.match(/^ide:\/\/editor-integrations\/approval-workflow\/candidate\/([^/]+)\/items$/);
      if (candidateItemsMatch) {
        var item = findCandidateSourceItem(decodeURIComponent(candidateItemsMatch[1]));
        return integrationResponse({ items: item && Array.isArray(item.items) ? item.items : [] });
      }
      var candidateCategoryMatch = url.match(/^ide:\/\/editor-integrations\/approval-workflow\/candidate\/([^/]+)\/category$/);
      if (candidateCategoryMatch) {
        return integrationResponse({ items: [] });
      }
      if (url === 'ide://editor-integrations/business/field-types/primary' || /\/field-types\/primary(?:[?#].*)?$/.test(url)) {
        return integrationResponse(businessFieldTypeOptions('primary'));
      }
      if (url === 'ide://editor-integrations/business/field-types/normal' || /\/field-types\/normal(?:[?#].*)?$/.test(url)) {
        return integrationResponse(businessFieldTypeOptions('normal'));
      }
      var businessFieldTypeUiSchemaMatch = url.match(/^ide:\/\/editor-integrations\/business\/field-types\/([^/?#]+)\/ui-schema(?:[?#].*)?$/)
        || url.match(/\/field-types\/([^/?#]+)\/ui-schema(?:[?#].*)?$/);
      if (businessFieldTypeUiSchemaMatch) {
        return integrationResponse(businessFieldTypeUiSchema(safeDecodeURIComponent(businessFieldTypeUiSchemaMatch[1])));
      }
      return Promise.reject(new Error('Unsupported offline editor API: ' + url));
    };
  }

  function createEditorComponentUtils() {
    var componentEnv;
    function notifyComponent(type, message) {
      componentEnv.notify(type, message);
    }
    function findAmisRuntimeFunction(name) {
      if (global.OuroborosAmisEditorRuntime && typeof global.OuroborosAmisEditorRuntime[name] === 'function') {
        return global.OuroborosAmisEditorRuntime[name];
      }
      if (!global.amisRequire) {
        return undefined;
      }
      var moduleNames = ['amis', 'amis-core'];
      for (var index = 0; index < moduleNames.length; index += 1) {
        try {
          var module = global.amisRequire(moduleNames[index]);
          if (module && typeof module[name] === 'function') {
            return module[name].bind(module);
          }
        } catch (_error) {}
      }
      return undefined;
    }

    function createAmisDecorator(name) {
      return function (config) {
        var decorator = findAmisRuntimeFunction(name);
        if (typeof decorator === 'function') {
          return decorator(config);
        }
        return function (Component) {
          return Component;
        };
      };
    }

    function callAmisRuntimeFunction(name) {
      var fn = findAmisRuntimeFunction(name);
      if (typeof fn !== 'function') {
        return undefined;
      }
      return fn.apply(undefined, Array.prototype.slice.call(arguments, 1));
    }

    var utils = {
      amisUtils: { createObject: createObject },
      FormItem: createAmisDecorator('FormItem'),
      Renderer: createAmisDecorator('Renderer'),
      OptionsControl: createAmisDecorator('OptionsControl'),
      registerAction: function (actionType, action) {
        return callAmisRuntimeFunction('registerAction', actionType, action);
      },
      addRule: function (ruleName, validatorFn, errorMessage) {
        return callAmisRuntimeFunction('addRule', ruleName, validatorFn, errorMessage);
      },
      toast: {
        success: function (message) { notifyComponent('success', message); },
        info: function (message) { notifyComponent('info', message); },
        warning: function (message) { notifyComponent('warning', message); },
        warn: function (message) { notifyComponent('warning', message); },
        error: function (message) { notifyComponent('error', message); },
      },
      confirm: function () { return Promise.resolve(true); },
      alert: function (message) { notify('info', message); },
      prompt: function () { return Promise.resolve(''); },
      openDialog: function () { return Promise.resolve(false); },
      openDrawer: function () { return Promise.resolve(false); },
      request: function (api) {
        var env = componentEnv || {};
        if (typeof env.fetcher === 'function') {
          return env.fetcher(api);
        }
        return emptyIntegrationResponse('IDE metadata editor has no request handler for this component.');
      },
      hasAuth: function () { return true; },
      renderAmisSchema: function (containerEl, schema, props, env) {
        var embed = getAmisEmbed();
        return embed ? embed(containerEl, schema, props || {}, env || componentEnv || {}) : undefined;
      },
      setEnv: function (env) {
        componentEnv = env;
      },
    };
    return utils;
  }

  function createAmisFetcher() {
    var fetcher = createEditorIntegrationFetcher();
    return function (api, data) { return fetcher(api, data).then(function (payload) { return { data: payload }; }); };
  }

  function createAmisEnv(state, diagnostics, onNotify) {
    return {
      theme: state.theme,
      locale: state.locale,
      fetcher: createAmisFetcher(),
      notify: onNotify,
      alert: function (message) {
        if (diagnostics) {
          diagnostics.textContent = String(message);
        }
      },
      confirm: function () { return Promise.resolve(true); },
      openDialog: function () { return undefined; },
      openDrawer: function () { return undefined; },
    };
  }

  function ensureEditorComponentLibraries(state, env) {
    if (!state.editorComponentUtils) {
      state.editorComponentUtils = createEditorComponentUtils();
    }
    state.editorComponentUtils.setEnv(env);
    if (state.editorComponentLibrariesMounted) {
      return undefined;
    }
    if (state.editorComponentLibrariesMounting) {
      return state.editorComponentLibrariesMounting;
    }
    var pendingMounts = [];
    ['logicflow-app', 'workflow-frontend-app'].forEach(function (globalName) {
      var library = global[globalName];
      if (!library) {
        return;
      }
      try {
        var mountLibrary = function () {
          if (typeof library.mount !== 'function') {
            return undefined;
          }
          return library.mount({ __utils__: state.editorComponentUtils });
        };
        var lifecycleResult;
        if (typeof library.bootstrap === 'function') {
          var bootstrapResult = library.bootstrap({});
          lifecycleResult = bootstrapResult && typeof bootstrapResult.then === 'function'
            ? bootstrapResult.then(mountLibrary)
            : mountLibrary();
        } else {
          lifecycleResult = mountLibrary();
        }
        if (lifecycleResult && typeof lifecycleResult.then === 'function') {
          pendingMounts.push(lifecycleResult);
        }
      } catch (error) {
        if (global.console && typeof global.console.warn === 'function') {
          global.console.warn('Unable to mount editor component library ' + globalName, error);
        }
      }
    });
    if (!pendingMounts.length) {
      state.editorComponentLibrariesMounted = true;
      return undefined;
    }
    state.editorComponentLibrariesMounting = Promise.all(pendingMounts).then(function () {
      state.editorComponentLibrariesMounted = true;
      state.editorComponentLibrariesMounting = undefined;
    }).catch(function (error) {
      state.editorComponentLibrariesMounting = undefined;
      throw error;
    });
    return state.editorComponentLibrariesMounting;
  }

  function normalizeTheme(theme) {
    return theme === 'dark' ? 'dark' : 'cxd';
  }

  function shouldForceLightTheme(editorKind, metadataType) {
    return editorKind === 'amis-editor' || metadataType === 'logicflow';
  }

  function normalizeThemeForEditorKind(theme, editorKind, metadataType) {
    return shouldForceLightTheme(editorKind, metadataType) ? 'cxd' : normalizeTheme(theme);
  }

  function normalizeLocale(locale) {
    return typeof locale === 'string' && locale.trim() ? locale.replace('_', '-') : 'zh-CN';
  }

  function hasOwn(value, key) {
    return Object.prototype.hasOwnProperty.call(value, key);
  }

  function isDocumentContainer(value) {
    return Array.isArray(value) || isRecord(value);
  }

  function isUiSchemaWrapper(value) {
    return isRecord(value) && isRecord(value.schema);
  }

  function shouldEditWrappedUiSchema(state) {
    return state.editorKind === 'amis-editor' && state.documentShape !== 'array' && isUiSchemaWrapper(state.currentValue);
  }

  function getVisualEditorValue(state) {
    return shouldEditWrappedUiSchema(state) ? state.currentValue.schema : state.currentValue;
  }

  function getUiSchemaWrapperFields(state) {
    if (!shouldEditWrappedUiSchema(state)) {
      return undefined;
    }
    return {
      name: typeof state.currentValue.name === 'string' ? state.currentValue.name : '',
      title: typeof state.currentValue.title === 'string' ? state.currentValue.title : '',
      description: typeof state.currentValue.description === 'string' ? state.currentValue.description : '',
    };
  }

  function mergePreservingKeyOrder(current, patch) {
    if (Array.isArray(current) && Array.isArray(patch)) {
      return patch.map(function (item, index) {
        return mergePreservingKeyOrder(current[index], item);
      });
    }
    if (!isRecord(current) || !isRecord(patch)) {
      return normalizeDocument(patch);
    }
    var next = {};
    Object.keys(current).forEach(function (key) {
      next[key] = hasOwn(patch, key) ? mergePreservingKeyOrder(current[key], patch[key]) : normalizeDocument(current[key]);
    });
    Object.keys(patch).forEach(function (key) {
      if (!hasOwn(current, key)) {
        next[key] = normalizeDocument(patch[key]);
      }
    });
    return next;
  }

  function valuesEqual(left, right) {
    if (left === right) {
      return true;
    }
    if (Array.isArray(left) && Array.isArray(right)) {
      return left.length === right.length && left.every(function (value, index) {
        return valuesEqual(value, right[index]);
      });
    }
    if (isRecord(left) && isRecord(right)) {
      var leftKeys = Object.keys(left);
      var rightKeys = Object.keys(right);
      return leftKeys.length === rightKeys.length && leftKeys.every(function (key) {
        return hasOwn(right, key) && valuesEqual(left[key], right[key]);
      });
    }
    return false;
  }

  function createValuePatch(base, current) {
    var operations = [];

    function collect(before, after, path, arrayContexts) {
      if (valuesEqual(before, after)) {
        return;
      }
      if (Array.isArray(before) && Array.isArray(after)) {
        var nextArrayContexts = arrayContexts.concat({
          path: path.slice(),
          expected: normalizeDocument(before),
        });
        var commonLength = Math.min(before.length, after.length);
        for (var index = 0; index < commonLength; index += 1) {
          collect(before[index], after[index], path.concat(index), nextArrayContexts);
        }
        for (var removedIndex = before.length - 1; removedIndex >= after.length; removedIndex -= 1) {
          operations.push({
            kind: 'delete',
            path: path.concat(removedIndex),
            expected: normalizeDocument(before[removedIndex]),
            expectsExisting: true,
            arrayContexts: nextArrayContexts,
          });
        }
        for (var addedIndex = before.length; addedIndex < after.length; addedIndex += 1) {
          operations.push({
            kind: 'set',
            path: path.concat(addedIndex),
            value: normalizeDocument(after[addedIndex]),
            expectsExisting: false,
            arrayContexts: nextArrayContexts,
          });
        }
        return;
      }
      if (isRecord(before) && isRecord(after)) {
        Object.keys(before).forEach(function (key) {
          if (!hasOwn(after, key)) {
            operations.push({
              kind: 'delete',
              path: path.concat(key),
              expected: normalizeDocument(before[key]),
              expectsExisting: true,
              arrayContexts: arrayContexts,
            });
          }
        });
        Object.keys(after).forEach(function (key) {
          if (hasOwn(before, key)) {
            collect(before[key], after[key], path.concat(key), arrayContexts);
          } else {
            operations.push({
              kind: 'set',
              path: path.concat(key),
              value: normalizeDocument(after[key]),
              expectsExisting: false,
              arrayContexts: arrayContexts,
            });
          }
        });
        return;
      }
      operations.push({
        kind: 'set',
        path: path.slice(),
        value: normalizeDocument(after),
        expected: normalizeDocument(before),
        expectsExisting: true,
        arrayContexts: arrayContexts,
      });
    }

    collect(base, current, [], []);
    return operations;
  }

  function valueAtPath(value, path) {
    var current = value;
    for (var index = 0; index < path.length; index += 1) {
      if (current === undefined || current === null || !hasOwn(current, path[index])) {
        return undefined;
      }
      current = current[path[index]];
    }
    return current;
  }

  function applyValuePatch(base, operations) {
    var result = normalizeDocument(base);
    var snapshot = normalizeDocument(base);
    var conflict = false;
    (operations || []).forEach(function (operation) {
      if (conflict) {
        return;
      }
      if (!operation.path.length) {
        if (!valuesEqual(result, operation.expected)) {
          conflict = true;
          return;
        }
        result = operation.kind === 'delete' ? undefined : normalizeDocument(operation.value);
        return;
      }
      if ((operation.arrayContexts || []).some(function (context) {
        return !valuesEqual(valueAtPath(snapshot, context.path), context.expected);
      })) {
        conflict = true;
        return;
      }
      var parent = result;
      for (var index = 0; index < operation.path.length - 1; index += 1) {
        if (parent === undefined || parent === null || (!isRecord(parent) && !Array.isArray(parent))) {
          conflict = true;
          return;
        }
        parent = parent[operation.path[index]];
      }
      if (parent === undefined || parent === null || (!isRecord(parent) && !Array.isArray(parent))) {
        conflict = true;
        return;
      }
      var key = operation.path[operation.path.length - 1];
      var indexKey = Array.isArray(parent) ? Number(key) : key;
      if (Array.isArray(parent) && (!Number.isInteger(indexKey) || indexKey < 0 || indexKey > parent.length)) {
        conflict = true;
        return;
      }
      var exists = Array.isArray(parent)
        ? Number.isInteger(indexKey) && indexKey >= 0 && indexKey < parent.length && hasOwn(parent, indexKey)
        : hasOwn(parent, key);
      if (exists !== operation.expectsExisting) {
        conflict = true;
        return;
      }
      if (operation.kind === 'delete') {
        if (Array.isArray(parent)) {
          parent.splice(indexKey, 1);
        } else {
          delete parent[key];
        }
      } else if (Array.isArray(parent) && indexKey === parent.length) {
        parent.push(normalizeDocument(operation.value));
      } else {
        parent[indexKey] = normalizeDocument(operation.value);
      }
    });
    return conflict ? { conflict: true } : { value: result, conflict: false };
  }

  function mount(options) {
    var transport = options.transport || {};
    var state = {
      revision: '0',
      currentText: '',
      currentValue: undefined,
      baseValue: undefined,
      localPatch: [],
      metadataType: undefined,
      editorSchema: undefined,
      editorKind: 'form',
      amisEditorApp: undefined,
      amisEditorMountRoot: undefined,
      amisEditorWrapperActive: undefined,
      uiSchemaWrapperInputs: undefined,
      uiSchemaWrapperFieldsRoot: undefined,
      uiSchemaWrapperToggle: undefined,
      uiSchemaWrapperCollapsed: false,
      editorComponentLibrariesMounted: false,
      editorComponentLibrariesMounting: undefined,
      editorComponentUtils: undefined,
      theme: normalizeTheme(options.theme),
      locale: normalizeLocale(options.locale),
      dirty: false,
      pendingState: undefined,
      saveTimer: undefined,
      saving: false,
      queuedSave: false,
      lastSaveError: undefined,
      diagnosticsMessage: '',
      diagnosticsPinned: false,
      amisEditorDragFallback: options.amisEditorDragFallback,
    };
    var amisRoot = byId(options.amisRootId || 'metadata-amis-root');
    var diagnostics = byId(options.diagnosticsId || 'metadata-diagnostics');
    var diagnosticsIndicator = byId(options.diagnosticsIndicatorId || 'metadata-diagnostics-indicator');
    var diagnosticsMessage = byId(options.diagnosticsMessageId || 'metadata-diagnostics-message');
    var diagnosticsClose = byId(options.diagnosticsCloseId || 'metadata-diagnostics-close');
    var dirty = byId(options.dirtyId || 'metadata-dirty');
    var importToolbar = byId(options.importToolbarId || 'metadata-import-toolbar');
    var importButton = byId(options.importButtonId || 'metadata-import-button');
    var importFile = byId(options.importFileId || 'metadata-import-file');
    var importHint = byId(options.importHintId || 'metadata-import-hint');
    var importStatus = byId(options.importStatusId || 'metadata-import-status');

    function applyThemeClass() {
      var isDark = state.theme === 'dark';
      var cxdLink = byId(options.cxdThemeLinkId || 'metadata-amis-theme-cxd');
      var darkLink = byId(options.darkThemeLinkId || 'metadata-amis-theme-dark');
      function setThemeLinkEnabled(link, enabled) {
        if (!link) {
          return;
        }
        link.disabled = !enabled;
        link.media = enabled ? 'all' : 'not all';
        if (link.sheet) {
          link.sheet.disabled = !enabled;
        }
      }
      if (cxdLink) {
        setThemeLinkEnabled(cxdLink, !isDark);
      }
      if (darkLink) {
        setThemeLinkEnabled(darkLink, isDark);
      }
      if (global.document && global.document.body && global.document.body.classList) {
        global.document.body.classList.toggle('metadata-theme-dark', isDark);
        global.document.body.classList.toggle('metadata-theme-light', !isDark);
        global.document.body.classList.toggle('metadata-amis-editor-light', state.editorKind === 'amis-editor');
      }
    }

    function setTheme(theme) {
      var nextTheme = normalizeThemeForEditorKind(theme, state.editorKind, state.metadataType);
      if (state.theme === nextTheme) {
        applyThemeClass();
        return;
      }
      state.theme = nextTheme;
      applyThemeClass();
      if (state.editorKind === 'amis-editor' && state.amisEditorApp) {
        state.amisEditorApp.setTheme(nextTheme);
        return;
      }
      renderAmisEditor();
    }

    function setDirty(value) {
      state.dirty = value;
      if (dirty) {
        dirty.textContent = value ? 'Unsaved changes' : '';
      }
    }

    function toggleElementClass(element, className, enabled) {
      if (!element) {
        return;
      }
      if (element.classList && typeof element.classList.toggle === 'function') {
        element.classList.toggle(className, enabled);
        return;
      }
      var classes = String(element.className || '').split(/\s+/).filter(Boolean);
      var hasClass = classes.indexOf(className) >= 0;
      if (enabled && !hasClass) {
        classes.push(className);
      } else if (!enabled && hasClass) {
        classes = classes.filter(function (item) { return item !== className; });
      }
      element.className = classes.join(' ');
    }

    function updateDiagnosticsDisclosure() {
      var expanded = Boolean(state.diagnosticsMessage && state.diagnosticsPinned);
      toggleElementClass(diagnostics, 'is-pinned', expanded);
      if (diagnosticsIndicator) {
        diagnosticsIndicator.setAttribute('aria-expanded', expanded ? 'true' : 'false');
      }
      if (diagnosticsClose) {
        diagnosticsClose.hidden = !expanded;
      }
    }

    function setDiagnosticsPinned(pinned) {
      state.diagnosticsPinned = Boolean(pinned && state.diagnosticsMessage);
      updateDiagnosticsDisclosure();
    }

    function setDiagnostics(items) {
      var message = formatDiagnostics(items || []);
      if (!message || message !== state.diagnosticsMessage) {
        state.diagnosticsPinned = false;
      }
      state.diagnosticsMessage = message;
      var messageTarget = diagnosticsMessage || diagnostics;
      if (messageTarget) {
        messageTarget.textContent = message;
      }
      if (diagnostics) {
        diagnostics.hidden = !message;
      }
      if (diagnosticsIndicator) {
        diagnosticsIndicator.title = message;
      }
      updateDiagnosticsDisclosure();
    }

    function reportNotification(type, message) {
      notify(type, message);
      if (type === 'error' || type === 'warning') {
        setDiagnostics([{ severity: type, code: 'editor-notification', message: String(message) }]);
        setDiagnosticsPinned(true);
      }
    }

    function setImportStatus(message) {
      if (importStatus) {
        importStatus.textContent = message || '';
      }
    }

    function updateImportToolbar() {
      var config = IMPORT_CONFIGS[state.metadataType];
      if (importToolbar) {
        importToolbar.hidden = !config;
      }
      if (importButton) {
        importButton.disabled = !config;
      }
      if (importHint) {
        importHint.textContent = config ? config.hint : '';
      }
      if (!config) {
        setImportStatus('');
      }
    }

    function setConflict(nextState) {
      state.pendingState = nextState;
      setDiagnostics([
        {
          severity: 'warning',
          code: 'revision-conflict',
          message: 'Document changed outside this editor. Local unsaved changes were kept.',
        },
      ]);
    }

    function clearConflict() {
      state.pendingState = undefined;
    }

    function clearSaveTimer() {
      if (state.saveTimer && typeof global.clearTimeout === 'function') {
        global.clearTimeout(state.saveTimer);
      }
      state.saveTimer = undefined;
    }

    function applySavedState(nextState, savedText) {
      if (!nextState || !nextState.document) {
        return;
      }
      state.revision = nextState.document.revision || state.revision;
      if (nextState.editorSchema) {
        state.editorSchema = nextState.editorSchema;
      }
      state.metadataType = nextState.document.type || state.metadataType;
      state.editorKind = nextState.editorKind || state.editorKind || 'form';
      state.theme = normalizeThemeForEditorKind(state.theme, state.editorKind, state.metadataType);
      applyThemeClass();
      updateImportToolbar();
      setDiagnostics(nextState.document.diagnostics || []);
      if (state.currentText === savedText || state.currentText === (nextState.document.text || '')) {
        state.currentText = nextState.document.text || savedText;
        state.currentValue = nextState.document.value;
        state.baseValue = clone(nextState.document.value);
        state.localPatch = [];
        state.queuedSave = false;
        state.lastSaveError = undefined;
        setDirty(false);
        clearConflict();
      } else {
        state.baseValue = clone(nextState.document.value);
        state.localPatch = createValuePatch(state.baseValue, state.currentValue);
        setDirty(true);
      }
    }

    function rebaseConflict(nextState) {
      if (!nextState || !nextState.document) {
        setDiagnostics([{ severity: 'error', code: 'revision-conflict', message: 'Unable to load the latest document state.' }]);
        return;
      }
      var externalValue = nextState.document.value;
      if (!isDocumentContainer(externalValue)) {
        setDiagnostics([
          {
            severity: 'error',
            code: 'revision-conflict-manual',
            message: 'The latest external document is not a JSON object. Local form changes were kept for manual reconciliation.',
          },
        ]);
        return;
      }
      var patchResult = applyValuePatch(externalValue, state.localPatch);
      if (patchResult.conflict) {
        setDiagnostics([
          {
            severity: 'error',
            code: 'revision-conflict-manual',
            message: 'The latest external document changed the structure used by local form changes. Local changes were kept for manual reconciliation.',
          },
        ]);
        return;
      }
      var rebasedValue = patchResult.value;
      state.revision = nextState.document.revision || state.revision;
      state.metadataType = nextState.document.type || state.metadataType;
      state.baseValue = clone(externalValue);
      state.currentValue = rebasedValue;
      state.currentText = serializeDocument(state.currentValue);
      state.localPatch = createValuePatch(state.baseValue, state.currentValue);
      if (nextState.editorSchema) {
        state.editorSchema = nextState.editorSchema;
      }
      state.editorKind = nextState.editorKind || state.editorKind || 'form';
      state.theme = normalizeThemeForEditorKind(state.theme, state.editorKind, state.metadataType);
      applyThemeClass();
      updateImportToolbar();
      state.pendingState = undefined;
      state.queuedSave = false;
      renderAmisEditor();
      setDirty(true);
      setDiagnostics([
        {
          severity: 'warning',
          code: 'revision-conflict-rebased',
          message: 'External changes were loaded and local form changes will be saved again.',
        },
      ]);
      scheduleSave();
    }

    function recoverRevisionConflict(error) {
      var pendingState = state.pendingState;
      if (pendingState) {
        rebaseConflict(pendingState);
        return;
      }
      if (!transport.loadState) {
        state.lastSaveError = error;
        setDiagnostics([{ severity: 'error', code: 'revision-conflict', message: error && error.message ? error.message : String(error) }]);
        return;
      }
      Promise.resolve(transport.loadState()).then(function (nextState) {
        rebaseConflict(state.pendingState || nextState);
      }).catch(function (loadError) {
        state.lastSaveError = loadError;
        setDiagnostics([
          {
            severity: 'error',
            code: 'revision-conflict-reload',
            message: loadError && loadError.message ? loadError.message : String(loadError),
          },
        ]);
      });
    }

    function handleSaveError(error) {
      state.saving = false;
      if (error && error.code === 'revision-conflict') {
        recoverRevisionConflict(error);
        return true;
      }
      state.lastSaveError = error;
      setDiagnostics([{ severity: 'error', code: 'host-save', message: error && error.message ? error.message : String(error) }]);
      return false;
    }

    function flushSave() {
      clearSaveTimer();
      if (!transport.saveText || !state.dirty) {
        return;
      }
      if (state.saving) {
        state.queuedSave = true;
        return;
      }

      var savedText = state.currentText;
      var baseRevision = state.revision;
      var result;
      state.saving = true;
      state.queuedSave = false;
      state.lastSaveError = undefined;
      try {
        result = transport.saveText(savedText, baseRevision);
      } catch (error) {
        handleSaveError(error);
        return;
      }

      Promise.resolve(result).then(function (nextState) {
        state.saving = false;
        state.lastSaveError = undefined;
        applySavedState(nextState, savedText);
        if (state.queuedSave || state.currentText !== savedText) {
          scheduleSave();
        }
      }).catch(function (error) {
        var recoveringConflict = handleSaveError(error);
        if (!recoveringConflict && state.queuedSave) {
          scheduleSave();
        }
      });
    }

    function scheduleSave() {
      if (!transport.saveText) {
        return;
      }
      clearSaveTimer();
      var delay = typeof options.saveDelayMs === 'number' ? options.saveDelayMs : 200;
      if (delay <= 0 || typeof global.setTimeout !== 'function') {
        flushSave();
        return;
      }
      state.saveTimer = global.setTimeout(flushSave, delay);
    }

    function flushPendingChanges() {
      if (!transport.saveText) {
        return Promise.resolve();
      }
      clearSaveTimer();
      flushSave();
      return new Promise(function (resolve, reject) {
        var startedAt = Date.now();
        var timeoutMs = typeof options.flushTimeoutMs === 'number' ? options.flushTimeoutMs : 10000;

        function waitForSave() {
          if (!state.dirty && !state.saving && !state.saveTimer && !state.queuedSave) {
            resolve();
            return;
          }
          if (state.lastSaveError && !state.saving && !state.saveTimer) {
            reject(state.lastSaveError);
            return;
          }
          if (!state.saving && !state.saveTimer && state.dirty) {
            flushSave();
          }
          if (timeoutMs >= 0 && Date.now() - startedAt > timeoutMs) {
            reject(new Error('Timed out while flushing pending metadata editor changes.'));
            return;
          }
          if (typeof global.setTimeout === 'function') {
            global.setTimeout(waitForSave, 10);
          } else {
            Promise.resolve().then(waitForSave);
          }
        }

        waitForSave();
      });
    }

    function applyEditorValue(value) {
      if (value === undefined) {
        return;
      }
      var previousValue = state.currentValue;
      var nextEditorValue = unwrapMetadataRootValue(state, value);
      if (state.editorKind === 'amis-editor') {
        if (shouldEditWrappedUiSchema(state)) {
          var nextValue = normalizeDocument(state.currentValue);
          nextValue.schema = normalizeDocument(nextEditorValue);
          state.currentValue = nextValue;
        } else if (state.documentShape === 'array') {
          var nextVisualArray = Array.isArray(nextEditorValue)
            ? nextEditorValue
            : isRecord(nextEditorValue) && hasOwn(nextEditorValue, 'items')
              ? nextEditorValue.items
              : undefined;
          if (!Array.isArray(nextVisualArray)) {
            return;
          }
          state.currentValue = normalizeDocument(nextVisualArray);
        } else {
          state.currentValue = normalizeDocument(nextEditorValue);
        }
      } else if (state.documentShape === 'array') {
        var nextArray = Array.isArray(nextEditorValue)
          ? nextEditorValue
          : isRecord(nextEditorValue) && hasOwn(nextEditorValue, 'items')
            ? nextEditorValue.items
            : undefined;
        if (!Array.isArray(nextArray)) {
          return;
        }
        state.currentValue = mergePreservingKeyOrder(Array.isArray(state.currentValue) ? state.currentValue : [], nextArray);
      } else if (getMetadataRootField(state)) {
        state.currentValue = isRecord(nextEditorValue)
          ? mergePreservingKeyOrder(isRecord(state.currentValue) ? state.currentValue : {}, nextEditorValue)
          : normalizeDocument(nextEditorValue);
      } else {
        state.currentValue = mergePreservingKeyOrder(isRecord(state.currentValue) ? state.currentValue : {}, nextEditorValue);
      }
      var nextText = serializeDocument(state.currentValue);
      if (nextText === serializeDocument(previousValue)) {
        state.currentValue = previousValue;
        return;
      }
      state.currentText = nextText;
      state.localPatch = createValuePatch(state.baseValue, state.currentValue);
      state.lastSaveError = undefined;
      setDirty(true);
      scheduleSave();
    }

    function applyImportedValue(importResult) {
      if (!importResult || !isRecord(importResult.value)) {
        return;
      }
      state.currentValue = mergeImportValue(isRecord(state.currentValue) ? state.currentValue : {}, importResult.value, importResult.replaceKeys);
      state.currentText = serializeDocument(state.currentValue);
      state.localPatch = createValuePatch(state.baseValue, state.currentValue);
      state.lastSaveError = undefined;
      renderAmisEditor();
      setDirty(true);
      setDiagnostics([]);
      setImportStatus(importResult.summary || '导入完成。');
      scheduleSave();
    }

    function importRows(matrix) {
      if (!isSupportedImportType(state.metadataType)) {
        throw new Error('当前元数据类型不支持导入。');
      }
      applyImportedValue(createMetadataImportResult(state.metadataType, matrix, state.currentValue));
    }

    function handleImportFile(file) {
      if (!file || !isSupportedImportType(state.metadataType)) {
        return;
      }
      if (importButton) {
        importButton.disabled = true;
      }
      setImportStatus('正在导入...');
      readImportRowsFromFile(file).then(function (rows) {
        importRows(rows);
      }).catch(function (error) {
        setImportStatus('导入失败');
        setDiagnostics([
          {
            severity: 'error',
            code: 'metadata-import',
            message: error && error.message ? error.message : String(error),
          },
        ]);
      }).then(function () {
        updateImportToolbar();
      });
    }

    function applyUiSchemaWrapperFields(fields) {
      if (!shouldEditWrappedUiSchema(state) || !isRecord(fields)) {
        return;
      }
      var patch = {};
      ['name', 'title', 'description'].forEach(function (key) {
        if (typeof fields[key] === 'string') {
          patch[key] = fields[key];
        }
      });
      state.currentValue = mergePreservingKeyOrder(state.currentValue, patch);
      state.currentText = serializeDocument(state.currentValue);
      state.localPatch = createValuePatch(state.baseValue, state.currentValue);
      state.lastSaveError = undefined;
      setDirty(true);
      scheduleSave();
    }

    function createUiSchemaWrapperField(field) {
      var wrapper = global.document.createElement('label');
      var label = global.document.createElement('span');
      var input = global.document.createElement(field.multiline ? 'textarea' : 'input');
      wrapper.className = 'metadata-ui-schema-wrapper-field';
      label.className = 'metadata-ui-schema-wrapper-label';
      label.textContent = field.label;
      if (field.labelRemark) {
        var labelRemark = global.document.createElement('span');
        labelRemark.className = 'metadata-ui-schema-wrapper-label-remark';
        labelRemark.textContent = '?';
        labelRemark.setAttribute('title', field.labelRemark);
        labelRemark.setAttribute('aria-label', field.labelRemark);
        label.appendChild(labelRemark);
      }
      input.className = 'metadata-ui-schema-wrapper-input';
      input.setAttribute('name', field.name);
      if (!field.multiline) {
        input.setAttribute('type', 'text');
      }
      input.addEventListener('input', function () {
        var nextFields = {};
        nextFields[field.name] = input.value;
        applyUiSchemaWrapperFields(nextFields);
      });
      wrapper.appendChild(label);
      wrapper.appendChild(input);
      return { wrapper: wrapper, input: input };
    }

    function updateUiSchemaWrapperFields() {
      var inputs = state.uiSchemaWrapperInputs;
      var fields = getUiSchemaWrapperFields(state);
      if (!inputs || !fields) {
        return;
      }
      Object.keys(inputs).forEach(function (key) {
        if (inputs[key].value !== fields[key]) {
          inputs[key].value = fields[key];
        }
      });
      updateUiSchemaWrapperCollapsedState();
    }

    function updateUiSchemaWrapperCollapsedState() {
      if (state.uiSchemaWrapperFieldsRoot) {
        state.uiSchemaWrapperFieldsRoot.className = 'metadata-ui-schema-wrapper-fields' + (state.uiSchemaWrapperCollapsed ? ' is-collapsed' : '');
      }
      if (state.uiSchemaWrapperToggle) {
        state.uiSchemaWrapperToggle.className = 'metadata-ui-schema-wrapper-toggle' + (state.uiSchemaWrapperCollapsed ? ' is-collapsed' : '');
        state.uiSchemaWrapperToggle.setAttribute('aria-expanded', state.uiSchemaWrapperCollapsed ? 'false' : 'true');
        state.uiSchemaWrapperToggle.textContent = state.uiSchemaWrapperCollapsed ? '展开页面信息' : '收起页面信息';
      }
    }

    function setUiSchemaWrapperCollapsed(collapsed) {
      state.uiSchemaWrapperCollapsed = Boolean(collapsed);
      updateUiSchemaWrapperCollapsedState();
    }

    function createUiSchemaWrapperHeader() {
      var header = global.document.createElement('div');
      var title = global.document.createElement('span');
      var toggle = global.document.createElement('button');
      header.className = 'metadata-ui-schema-wrapper-header';
      title.className = 'metadata-ui-schema-wrapper-title';
      title.textContent = '页面信息';
      toggle.setAttribute('type', 'button');
      toggle.addEventListener('click', function () {
        setUiSchemaWrapperCollapsed(!state.uiSchemaWrapperCollapsed);
      });
      header.appendChild(title);
      header.appendChild(toggle);
      state.uiSchemaWrapperToggle = toggle;
      updateUiSchemaWrapperCollapsedState();
      return header;
    }

    function ensureAmisEditorMountRoot(wrapped) {
      if (state.amisEditorApp && state.amisEditorWrapperActive !== wrapped) {
        state.amisEditorApp.dispose();
        state.amisEditorApp = undefined;
        state.amisEditorMountRoot = undefined;
        state.uiSchemaWrapperInputs = undefined;
        state.uiSchemaWrapperFieldsRoot = undefined;
        state.uiSchemaWrapperToggle = undefined;
      }
      if (state.amisEditorApp && state.amisEditorMountRoot) {
        updateUiSchemaWrapperFields();
        return state.amisEditorMountRoot;
      }
      state.amisEditorWrapperActive = wrapped;
      state.uiSchemaWrapperInputs = undefined;
      state.uiSchemaWrapperFieldsRoot = undefined;
      state.uiSchemaWrapperToggle = undefined;
      amisRoot.innerHTML = '';
      if (!wrapped || !global.document || typeof global.document.createElement !== 'function') {
        state.amisEditorMountRoot = amisRoot;
        return state.amisEditorMountRoot;
      }
      var shell = global.document.createElement('div');
      var fieldsRoot = global.document.createElement('div');
      var editorRoot = global.document.createElement('div');
      var inputs = {};
      shell.className = 'metadata-ui-schema-editor-shell';
      fieldsRoot.className = 'metadata-ui-schema-wrapper-fields';
      editorRoot.className = 'metadata-ui-schema-editor-canvas';
      [
        { name: 'name', label: '名称', labelRemark: UI_SCHEMA_NAME_LABEL_REMARK },
        { name: 'title', label: '标题' },
        { name: 'description', label: '描述', multiline: true },
      ].forEach(function (field) {
        var result = createUiSchemaWrapperField(field);
        inputs[field.name] = result.input;
        fieldsRoot.appendChild(result.wrapper);
      });
      shell.appendChild(createUiSchemaWrapperHeader());
      shell.appendChild(fieldsRoot);
      shell.appendChild(editorRoot);
      amisRoot.appendChild(shell);
      state.uiSchemaWrapperInputs = inputs;
      state.uiSchemaWrapperFieldsRoot = fieldsRoot;
      state.amisEditorMountRoot = editorRoot;
      updateUiSchemaWrapperFields();
      return editorRoot;
    }

    function renderAmisEditor() {
      if (!amisRoot) {
        return;
      }
      if (amisRoot.classList) {
        amisRoot.classList.toggle('metadata-amis-editor-active', state.editorKind === 'amis-editor');
      }
      if (state.editorKind === 'amis-editor') {
        var amisEditorRuntime = global.OuroborosAmisEditorRuntime;
        if (!amisEditorRuntime || typeof amisEditorRuntime.mount !== 'function') {
          amisRoot.textContent = 'AMIS visual editor runtime failed to load.';
          return;
        }
        try {
          var wrappedUiSchema = shouldEditWrappedUiSchema(state);
          var editorRoot = ensureAmisEditorMountRoot(wrappedUiSchema);
          var editorState = {
            value: getVisualEditorValue(state),
            theme: state.theme,
            locale: state.locale,
          };
          if (!state.amisEditorApp) {
            state.amisEditorApp = amisEditorRuntime.mount({
              root: editorRoot,
              value: editorState.value,
              theme: editorState.theme,
              locale: editorState.locale,
              fetcher: createAmisFetcher(),
              dragFallback: state.amisEditorDragFallback,
              onChange: applyEditorValue,
              onError: function (error) {
                setDiagnostics([
                  {
                    severity: 'error',
                    code: 'amis-editor-render',
                    message: error && error.message ? error.message : String(error),
                  },
                ]);
              },
            });
          } else {
            updateUiSchemaWrapperFields();
            state.amisEditorApp.applyState(editorState);
          }
        } catch (error) {
          amisRoot.textContent = 'Unable to render AMIS visual editor.';
          setDiagnostics([
            {
              severity: 'error',
              code: 'amis-editor-render',
              message: error && error.message ? error.message : String(error),
            },
          ]);
        }
        return;
      }
      if (state.amisEditorApp) {
        state.amisEditorApp.dispose();
        state.amisEditorApp = undefined;
        state.amisEditorMountRoot = undefined;
        state.amisEditorWrapperActive = undefined;
        state.uiSchemaWrapperInputs = undefined;
        state.uiSchemaWrapperFieldsRoot = undefined;
        state.uiSchemaWrapperToggle = undefined;
      }
      amisRoot.innerHTML = '';
      var embed = getAmisEmbed();
      if (!embed || !state.editorSchema) {
        amisRoot.textContent = 'Unsupported metadata file';
        return;
      }
      try {
        var env = createAmisEnv(state, diagnostics, reportNotification);
        registerIdeFallbackFormItems();
        var componentLibraryMount = ensureEditorComponentLibraries(state, env);
        if (componentLibraryMount && typeof componentLibraryMount.then === 'function') {
          amisRoot.textContent = 'Loading metadata editor components...';
          componentLibraryMount.then(renderAmisEditor).catch(function (error) {
            amisRoot.textContent = 'Unable to load metadata editor components.';
            setDiagnostics([
              {
                severity: 'error',
                code: 'metadata-editor-component-library',
                message: error && error.message ? error.message : String(error),
              },
            ]);
          });
          return;
        }
        embed(
          amisRoot,
          normalizeEditorSchema(state.editorSchema),
          {
            data: Object.assign(global.OuroborosMetadataEditorHost && global.OuroborosMetadataEditorHost.service
              ? { appName: global.OuroborosMetadataEditorHost.service.appName } : {}, getEditorFormData(state)),
            locale: state.locale,
            onChange: function () {
              applyEditorValue(extractValueFromAmisChange(arguments));
            },
          },
          env,
        );
      } catch (error) {
        amisRoot.textContent = 'Unable to render metadata form.';
        setDiagnostics([
          {
            severity: 'error',
            code: 'amis-render',
            message: error && error.message ? error.message : String(error),
          },
        ]);
      }
    }

    function applyState(nextState, applyOptions) {
      if (!nextState || !nextState.document) {
        return;
      }
      var incomingRevision = nextState.document.revision || '0';
      var incomingText = nextState.document.text || '';
      if (state.dirty && !(applyOptions && applyOptions.force) && incomingRevision !== state.revision && incomingText === state.currentText) {
        applySavedState(nextState, state.currentText);
        return;
      }
      if (state.dirty && !(applyOptions && applyOptions.force) && incomingRevision !== state.revision) {
        setConflict(nextState);
        return;
      }
      state.revision = nextState.document.revision || '0';
      state.currentText = incomingText;
      state.currentValue = nextState.document.value;
      state.baseValue = clone(nextState.document.value);
      state.localPatch = [];
      state.metadataType = nextState.document.type;
      state.editorSchema = nextState.editorSchema;
      state.editorKind = nextState.editorKind || 'form';
      state.theme = normalizeThemeForEditorKind(state.theme, state.editorKind, state.metadataType);
      applyThemeClass();
      state.documentShape = nextState.documentShape || (Array.isArray(nextState.document.value) ? 'array' : 'object');
      updateImportToolbar();
      setDiagnostics(nextState.document.diagnostics || []);
      renderAmisEditor();
      setDirty(false);
      clearConflict();
    }

    if (options.initialState) {
      applyThemeClass();
      applyState(options.initialState);
    } else {
      applyThemeClass();
      updateImportToolbar();
    }

    if (importButton && importFile) {
      importButton.addEventListener('click', function () {
        importFile.value = '';
        if (typeof importFile.click === 'function') {
          importFile.click();
        }
      });
      importFile.addEventListener('change', function () {
        var file = importFile.files && importFile.files[0];
        handleImportFile(file);
      });
    }
    if (diagnosticsIndicator) {
      diagnosticsIndicator.addEventListener('click', function () {
        setDiagnosticsPinned(!state.diagnosticsPinned);
      });
    }
    if (diagnosticsClose) {
      diagnosticsClose.addEventListener('click', function () {
        setDiagnosticsPinned(false);
      });
    }

    return {
      applyState: applyState,
      applyDiagnostics: setDiagnostics,
      applyEditorValue: applyEditorValue,
      importRows: importRows,
      flushPendingChanges: flushPendingChanges,
      setTheme: setTheme,
      getText: function () {
        return state.currentText;
      },
      getRevision: function () {
        return state.revision;
      },
      getTheme: function () {
        return state.theme;
      },
      getLocale: function () {
        return state.locale;
      },
    };
  }

  global.OuroborosMetadataEditorRuntime = {
    mount: mount,
    serializeDocument: serializeDocument,
    extractValueFromAmisChange: function () {
      return extractValueFromAmisChange(arguments);
    },
    __test__: {
      normalizeDocument: normalizeDocument,
      serializeDocument: serializeDocument,
      extractValueFromAmisChange: extractValueFromAmisChange,
      mergePreservingKeyOrder: mergePreservingKeyOrder,
      createValuePatch: createValuePatch,
      applyValuePatch: applyValuePatch,
      normalizeEditorSchema: normalizeEditorSchema,
      normalizeTheme: normalizeTheme,
      normalizeLocale: normalizeLocale,
      isUiSchemaWrapper: isUiSchemaWrapper,
      getVisualEditorValue: getVisualEditorValue,
      getEditorFormData: getEditorFormData,
      unwrapMetadataRootValue: unwrapMetadataRootValue,
      getUiSchemaWrapperFields: getUiSchemaWrapperFields,
      parseDelimitedRows: parseDelimitedRows,
      createImportTable: createImportTable,
      createMetadataImportResult: createMetadataImportResult,
      mergeImportValue: mergeImportValue,
      parseWorkbookRows: parseWorkbookRows,
      createEditorIntegrationFetcher: createEditorIntegrationFetcher,
    },
  };
})(window);
