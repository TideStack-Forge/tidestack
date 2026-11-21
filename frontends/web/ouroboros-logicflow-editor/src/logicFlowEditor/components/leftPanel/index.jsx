import { useLocale } from '../../../useLocale.js'
import { t } from '../../../i18n.js'
import React, { useMemo, useState } from 'react'

import { getTerminalVisualState } from '@/node-define/displayState.mjs'
import { SvgIcon } from '@/icons/index.js'
import EditorPanel from '../panel/EditorPanel.jsx'

export default function LeftPanel({
  nodeDefineList = [],
  onStartDrag,
  visible = true,
  onRequestClose,
}) {
  const [collapsedGroup, setCollapsedGroup] = useState({})
  const [searchKeyword, setSearchKeyword] = useState('')
  const locale = useLocale()

  const groupedNodes = useMemo(() => {
    const normalizedKeyword = searchKeyword.trim().toLowerCase()
    const groups = []
    nodeDefineList.forEach((node) => {
      const searchableText = [
        node.label,
        t(node.label),
        node.type,
        node.name,
        node.scaffold?.name,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()

      if (normalizedKeyword && !searchableText.includes(normalizedKeyword)) {
        return
      }

      const nodeGroup = node.group || '通用'
      const existingGroup = groups.find((group) => group.group === nodeGroup)
      if (!existingGroup) {
        groups.push({
          group: nodeGroup,
          nodes: [node],
        })
      } else {
        existingGroup.nodes.push(node)
      }
    })
    return groups
  }, [nodeDefineList, searchKeyword, locale])

  function toggleCollapse(group) {
    setCollapsedGroup((previous) => ({
      ...previous,
      [group]: !previous[group],
    }))
  }

  function getContentStyle(group) {
    const collapsed = Boolean(collapsedGroup[group])
    return {
      maxHeight: collapsed ? '0' : 'none',
      paddingTop: collapsed ? '0' : '2px',
      paddingBottom: collapsed ? '0' : '25px',
      overflow: collapsed ? 'hidden' : 'visible',
    }
  }

  return (
    <EditorPanel
      side="left"
      visible={visible}
      data-testid="logicflow-panel-left"
      header={<>
        <label className="node-search-field">
          <i className="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
          <input
            type="search"
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            placeholder={t('搜索节点')}
            aria-label={t('搜索节点')}
            data-testid="logicflow-node-search"
          />
        </label>
        <button
          type="button"
          className="panel-close-button"
          data-testid="logicflow-left-panel-close"
          aria-label={t('隐藏节点面板')}
          onClick={() => onRequestClose?.()}>
          <i className="fa-solid fa-xmark"></i>
        </button>
      </>}
    >
      <div className="node-groups">
        {groupedNodes.length === 0 ? (
          <div className="node-search-empty">{t('无匹配节点')}</div>
        ) : (
          groupedNodes.map((group) => (
            <div key={group.group} className="node-group">
              <div className="group-title" onClick={() => toggleCollapse(group.group)}>
                <h4>{t(group.group)}</h4>
                <i
                  className={`fa-solid fa-chevron-right ${collapsedGroup[group.group] ? '' : 'rotate'}`}
                ></i>
              </div>
              <div className="group-content" style={getContentStyle(group.group)}>
                <ul className="node-list">
                  {group.nodes.map((node, index) => {
                    const terminalState = getTerminalVisualState(node)
                    const nodeItemClassName = [
                      'node-list-item',
                      terminalState.isTerminal ? 'node-list-item-terminal' : '',
                      terminalState.paletteClassName
                        ? `node-list-item-${terminalState.paletteClassName}`
                        : '',
                    ]
                      .filter(Boolean)
                      .join(' ')
                    const terminalNodeStyle = terminalState.isTerminal
                      ? {
                          '--logicflow-palette-node-fill': terminalState.bodyFill || '#F8FAFC',
                          '--logicflow-palette-node-stroke': terminalState.bodyStroke || '#94A3B8',
                        }
                      : undefined

                    return (
                      <li
                        key={`${node.type || node.label}-${index}`}
                        className={nodeItemClassName}
                        data-terminal-kind={terminalState.terminalKind || undefined}
                        style={terminalNodeStyle}
                        onMouseDown={(event) => onStartDrag?.(event, node)}
                        onTouchStart={(event) => {
                          event.stopPropagation()
                          onStartDrag?.(event, node)
                        }}
                      >
                        <div className={`icon ${terminalState.paletteClassName}`}>
                          <SvgIcon icon={node.icon || ''} />
                        </div>
                        <span className="node-name">{t(node.label)}</span>
                      </li>
                    )
                  })}
                </ul>
              </div>
            </div>
          ))
        )}
      </div>
    </EditorPanel>
  )
}
