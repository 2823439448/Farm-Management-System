// 文件: index.js (最终稳定版：修复 Chart.js 无法自动更新的 Bug)

const MAX_DATA_POINTS = 60;

let timeLabels = [];
let tempData = [];
let humidityData = [];
let chart;

const API_KEY = '07f1b15756b74cfdb9c135254252511';
const DEFAULT_CITY = '成都';


document.addEventListener('DOMContentLoaded', function() {

    const ctx = document.getElementById('tempHumidityChart');
    if (!ctx) {
        console.error("致命错误：无法找到 ID 为 'tempHumidityChart' 的 Canvas 元素。");
        const container = document.querySelector('.chart-container');
        if (container) {
            container.style.height = '350px';
        }
        return;
    }

    ctx.style.height = '350px';
    ctx.style.width = '100%';

    // 初始设置占位符
    if (timeLabels.length === 0) {
        timeLabels.push('加载中...');
        tempData.push(null);
        humidityData.push(null);
    }

    chart = new Chart(ctx.getContext('2d'), {
        type: 'line',
        data: {
            labels: timeLabels,
            datasets: [
                {
                    label: '温度 (℃)',
                    data: tempData,
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    yAxisID: 'yTemp',
                    fill: true,
                    tension: 0.1
                },
                {
                    label: '湿度 (%)',
                    data: humidityData,
                    borderColor: 'rgb(54, 162, 235)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    yAxisID: 'yHumid',
                    fill: true,
                    tension: 0.1
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                yTemp: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: '温度 (℃)' },
                    suggestedMin: 20, suggestedMax: 35
                },
                yHumid: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: '湿度 (%)' },
                    suggestedMin: 40, suggestedMax: 80,
                    grid: { drawOnChartArea: false }
                }
            },
            plugins: {
                tooltip: {
                    mode: 'index',
                    intersect: false,
                }
            },
            hover: {
                mode: 'nearest',
                intersect: true
            }
        }
    });

    // 启动数据获取循环
    fetchData();
    setInterval(fetchData, 5000);

    // 交互事件处理 (保持不变)
    document.getElementById('setHumidBtn').addEventListener('click', () => {
        const target = document.getElementById('targetHumid').value;
        if (target && !isNaN(target)) {
            alert(`💦 提高湿度指令已发送！目标湿度设定为 ${target}%。`);
            console.log(`发送提高湿度指令，目标 ${target}%...`);
        } else {
            alert('请输入有效的目标湿度！');
        }
    });

    document.getElementById('heatBtn').addEventListener('click', () => {
        const target = document.getElementById('targetTemp').value;
        if (target && !isNaN(target)) {
            alert(`🔥 升温指令已发送！目标温度设定为 ${target}℃。`);
            console.log(`发送升温指令，目标 ${target}℃...`);
        } else {
            alert('请输入有效的目标温度！');
        }
    });

    document.getElementById('getWeatherBtn').addEventListener('click', () => {
        const city = document.getElementById('cityInput').value.trim();
        if (city) fetchWeather(city);
    });

    document.getElementById('cityInput').value = DEFAULT_CITY;
    fetchWeather(DEFAULT_CITY);

    // 启动 AI 助手检查
    checkAICommand();
    // 移除此处对 checkAICommand 的重复调用，防止双重提示
    // setInterval(checkAICommand, 1000);
});


// ---- 修正后的 fetchData() (保持不变) ----
async function fetchData() {
    let shouldUpdateChart = false;

    try {
        const response = await fetch('/api/my-device-data', {
            method: 'GET',
            credentials: 'include'
        });

        let devicesData = [];
        let isErrorOrEmpty = false;

        if (response.status === 401) {
            console.error("❌ 错误 401: 未登录或会话过期，请重新登录。");
            isErrorOrEmpty = true;
        } else if (!response.ok) {
            console.error(`❌ HTTP 错误! 状态码: ${response.status}`);
            throw new Error(`HTTP 错误! 状态码: ${response.status}`);
        } else {
            devicesData = await response.json();
        }

        if (devicesData.length === 0 || isErrorOrEmpty) {
            // **修正 1：如果获取失败，不再清空数组，而是保留历史数据**
            // 只有当数组为空时，才设置占位符
            if (timeLabels.length === 0) {
                timeLabels.push('无数据');
                tempData.push(null);
                humidityData.push(null);
                shouldUpdateChart = true;
            }

            // 修正 DOM 元素错误：即使出错，也要确保有这个元素
            const deviceNameEl = document.getElementById('deviceName');
            if(deviceNameEl) {
                deviceNameEl.textContent = isErrorOrEmpty ? "未登录" : "设备离线/无数据";
            }

            // 如果只有占位符，强制更新一次图表，否则不更新（避免闪烁）
            if (timeLabels.length === 1 && timeLabels[0] === '无数据' && chart) {
                chart.update('none');
            }
            return;
        }

        // --- 有数据时的处理 ---
        const latestData = devicesData[0];

        const newTemp = Number(latestData.temperature);
        const newHumid = Number(latestData.humidity);
        const newLight = Number(latestData.light);
        const deviceName = latestData.deviceName || "未知设备"; // 确保有默认值

        // **修正 2：使用当前系统时间作为图表标签，确保图表流动**
        const now = new Date();
        const timeString = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}:${now.getSeconds().toString().padStart(2,'0')}`;

        // 移除占位符
        if (timeLabels.length === 1 && /加载中|无数据/.test(String(timeLabels[0]))) {
            timeLabels.length = 0;
            tempData.length = 0;
            humidityData.length = 0;
        }

        // 推入真实数据
        timeLabels.push(timeString);
        tempData.push(Number.isFinite(newTemp) ? newTemp : null);
        humidityData.push(Number.isFinite(newHumid) ? newHumid : null);

        // 保持最大点数
        while (timeLabels.length > MAX_DATA_POINTS) {
            timeLabels.shift();
            tempData.shift();
            humidityData.shift();
        }

        // 更新 DOM 显示 (添加对 null 的检查，防止再次出现 TypeError)
        const currentTempEl = document.getElementById('currentTemp');
        const currentHumidEl = document.getElementById('currentHumid');
        const currentPhEl = document.getElementById('currentPh');
        const deviceNameEl = document.getElementById('deviceName');

        if (currentTempEl && !isNaN(newTemp)) currentTempEl.textContent = newTemp.toFixed(1);
        if (currentHumidEl && !isNaN(newHumid)) currentHumidEl.textContent = newHumid.toFixed(0);
        if (currentPhEl && !isNaN(newLight)) currentPhEl.textContent = newLight.toFixed(1);
        if (deviceNameEl) deviceNameEl.textContent = deviceName;

        shouldUpdateChart = true;

    } catch (error) {
        console.error("❌ 获取传感器数据失败或解析错误:", error);
        // 捕获错误时，我们不再清空数组，保持图表不变
    } finally {
        // 强制更新图表
        if (chart && shouldUpdateChart) {
            // 无需重新赋值 chart.data.labels = timeLabels，因为引用已绑定
            chart.update('none');
            console.log("✅ Chart.js 已强制更新");
        }
    }
}
// --- fetchWeather / displayWeather / checkAICommand (保持不变) ---

async function fetchWeather(city) {
    const weatherInfoDiv = document.getElementById('weatherInfo');
    if (!API_KEY || API_KEY === "YOUR_API_KEY") {
        weatherInfoDiv.innerHTML = '<p style="color:red;">⚠️ 请先填写 WeatherAPI Key！</p>';
        return;
    }
    const apiUrl = `https://api.weatherapi.com/v1/current.json?key=${API_KEY}&q=${city}&lang=zh`;
    try {
        weatherInfoDiv.innerHTML = `<p>正在查询 ${city} 的天气...</p>`;
        const response = await fetch(apiUrl);
        if (!response.ok) { throw new Error(`无法获取 ${city} 的天气，请检查城市名称`); }
        const data = await response.json();
        displayWeather(data);
    } catch (error) {
        weatherInfoDiv.innerHTML = `<p style="color:red;">错误：${error.message}</p>`;
        console.error("WeatherAPI 获取失败：", error);
    }
}

function displayWeather(data) {
    const weatherInfoDiv = document.getElementById('weatherInfo');
    const temp = data.current.temp_c;
    const description = data.current.condition.text;
    const iconUrl = "https:" + data.current.condition.icon;
    weatherInfoDiv.innerHTML = `
        <p>
            <img src="${iconUrl}" class="weather-icon">
            <strong>${data.location.name}</strong>（实时）
        </p>
        <p>🌡️ 温度：<strong>${temp} ℃</strong></p>
        <p>☁️ 描述：${description}</p>
        <p>💧 湿度：${data.current.humidity} %</p>
        <p>💨 风速：${data.current.wind_kph} km/h</p>
    `;
}

function checkAICommand() {
    const aiSetTemp = localStorage.getItem("aiSetTemp");
    const aiAutoHeat = localStorage.getItem("aiAutoHeat");
    const aiSetHumid = localStorage.getItem("aiSetHumid");
    const aiAutoHumid = localStorage.getItem("aiAutoHumid");

    let tempExecuted = false;

    if (aiSetTemp && aiAutoHeat === "true" && !isNaN(parseFloat(aiSetTemp))) {
        const targetTempInput = document.getElementById('targetTemp');
        if (!isNaN(parseFloat(aiSetTemp))) { targetTempInput.value = parseFloat(aiSetTemp); }
        else {
            console.error("AI 设定的温度值无效:", aiSetTemp);
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            return;
        }

        setTimeout(() => {
            document.getElementById('heatBtn').click();
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            console.log(`AI助手指令(1/2)已执行：目标温度设置为 ${aiSetTemp}℃ 并发送升温指令。`);
        }, 500);
        tempExecuted = true;
    } else {
        localStorage.removeItem("aiSetTemp");
        localStorage.removeItem("aiAutoHeat");
    }

    if (aiSetHumid && aiAutoHumid === "true" && !isNaN(parseFloat(aiSetHumid))) {
        const targetHumidInput = document.getElementById('targetHumid');
        targetHumidInput.value = parseFloat(aiSetHumid);

        const delay = tempExecuted ? 1500 : 500;

        setTimeout(() => {
            document.getElementById('setHumidBtn').click();
            localStorage.removeItem("aiSetHumid");
            localStorage.removeItem("aiAutoHumid");
            console.log(`AI助手指令(${tempExecuted ? '2/2' : '1/1'})已执行：目标湿度设置为 ${aiSetHumid}% 并发送提高湿度指令。`);
        }, delay);
    } else {
        localStorage.removeItem("aiSetHumid");
        localStorage.removeItem("aiAutoHumid");
    }
}